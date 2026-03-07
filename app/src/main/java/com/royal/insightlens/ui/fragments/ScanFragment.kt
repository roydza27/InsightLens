package com.royal.insightlens.ui.fragments

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.royal.insightlens.R
import com.royal.insightlens.data.local.AppDatabase
import com.royal.insightlens.data.repository.BookRepository
import com.royal.insightlens.ui.activities.BookDetailsActivity
import com.royal.insightlens.ui.viewmodel.ScanUiState
import com.royal.insightlens.ui.viewmodel.ScanViewModel
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@ExperimentalGetImage
class ScanFragment : Fragment() {

    companion object {
        private const val TAG = "ScanFragment"
    }

    private val viewModel: ScanViewModel by viewModels {
        ScanViewModel.Factory(
            BookRepository(AppDatabase.getInstance(requireContext()).bookDao())
        )
    }

    private lateinit var previewView: PreviewView
    private lateinit var analyzingPill: ConstraintLayout
    private lateinit var guidanceCard: ConstraintLayout
    private lateinit var infoContainer: ConstraintLayout
    private lateinit var statusTitle: TextView
    private lateinit var percentText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var captureBtn: ImageButton
    private lateinit var flashBtn: ImageButton

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var textRecognizer: TextRecognizer
    private var flashEnabled = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
        else Toast.makeText(
            requireContext(),
            getString(R.string.camera_permission_required),
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_scan, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        setupCamera()
        setupClickListeners()
        observeUiState()
    }

    private fun bindViews(view: View) {
        previewView    = view.findViewById(R.id.scan_preview_view)
        analyzingPill  = view.findViewById(R.id.scan_analyzing_pill)
        guidanceCard   = view.findViewById(R.id.scan_guidance_card)
        infoContainer  = view.findViewById(R.id.scan_info_container)
        statusTitle    = view.findViewById(R.id.scan_status_title)
        percentText    = view.findViewById(R.id.scan_percent_text)
        progressBar    = view.findViewById(R.id.scan_progress)
        captureBtn     = view.findViewById(R.id.scan_capture_btn)
        flashBtn       = view.findViewById(R.id.scan_flash_btn)
    }

    private fun setupCamera() {
        cameraExecutor = Executors.newSingleThreadExecutor()
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        if (hasCameraPermission()) startCamera()
        else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(
        requireContext(), Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(requireContext())
        future.addListener({
            bindCameraUseCases(future.get())
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun bindCameraUseCases(provider: ProcessCameraProvider) {
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    processFrame(imageProxy)
                }
            }

        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                viewLifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera use cases: ${e.message}")
        }
    }

    private fun processFrame(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        textRecognizer.process(image)
            .addOnSuccessListener { ocrResult ->
                viewModel.onOcrResult(
                    ocrResult   = ocrResult,
                    imageWidth  = imageProxy.width,
                    imageHeight = imageProxy.height
                )
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun setupClickListeners() {
        captureBtn.setOnClickListener { viewModel.onManualCapture() }
        flashBtn.setOnClickListener {
            flashEnabled = !flashEnabled
            flashBtn.setImageResource(
                if (flashEnabled) R.drawable.ic_flash else R.drawable.ic_flashlight
            )
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> renderState(state) }
            }
        }
    }

    private fun renderState(state: ScanUiState) {
        when (state) {
            is ScanUiState.Idle -> {
                analyzingPill.visibility = View.GONE
                infoContainer.visibility = View.GONE
                guidanceCard.visibility  = View.VISIBLE
                progressBar.progress     = 0
                percentText.text         = ""
                statusTitle.setText(R.string.scan_status_ready)
            }

            is ScanUiState.Scanning -> {
                val pct = (state.progress * 100).toInt()
                analyzingPill.visibility = View.VISIBLE
                infoContainer.visibility = View.VISIBLE
                guidanceCard.visibility  = View.VISIBLE
                progressBar.progress     = pct
                percentText.text         = getString(R.string.percent_format, pct)
                statusTitle.setText(R.string.scan_status_scanning)
            }

            is ScanUiState.Success -> {
                analyzingPill.visibility = View.GONE
                infoContainer.visibility = View.GONE
                val intent = Intent(requireContext(), BookDetailsActivity::class.java)
                intent.putExtra(BookDetailsActivity.EXTRA_VOLUME_ID, state.book.volumeId)
                startActivity(intent)
            }

            is ScanUiState.NoMatch -> {
                analyzingPill.visibility = View.GONE
                infoContainer.visibility = View.VISIBLE
                progressBar.progress     = 0
                percentText.text         = ""
                statusTitle.setText(R.string.scan_status_no_match)
            }

            is ScanUiState.Error -> {
                analyzingPill.visibility = View.GONE
                showScanFailedDialog(state.message)
            }

            is ScanUiState.Cooldown -> {
                analyzingPill.visibility = View.GONE
                infoContainer.visibility = View.VISIBLE
                progressBar.progress     = 100
                percentText.text         = ""
                statusTitle.setText(R.string.scan_status_cooldown)
            }
        }
    }

    private fun showScanFailedDialog(message: String) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_scan_failed, requireView() as ViewGroup, false)

        dialogView.findViewById<TextView>(R.id.dialog_description).text = message

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(
            R.id.dialog_retry_btn
        ).setOnClickListener {
            viewModel.onRetry()
            dialog.dismiss()
        }

        dialogView.findViewById<com.google.android.material.button.MaterialButton>(
            R.id.dialog_dismiss_btn
        ).setOnClickListener {
            viewModel.onDismiss()
            dialog.dismiss()
        }

        dialog.setContentView(dialogView)
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        textRecognizer.close()
    }
}