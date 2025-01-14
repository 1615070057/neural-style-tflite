package com.rithikjain.neuralstyletransfer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import com.livinglifetechway.quickpermissions_kotlin.runWithPermissions
import com.theartofdev.edmodo.cropper.CropImage
import com.theartofdev.edmodo.cropper.CropImageView
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlin.coroutines.CoroutineContext

class MainActivity : AppCompatActivity(), CoroutineScope {
    private lateinit var mJob: Job
    override val coroutineContext: CoroutineContext
        get() = mJob + Dispatchers.Main

    private lateinit var styleTransferModel: StyleTransferModel
    private var isModelRunning = false
    private var contentImage: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        progressBar.visibility = View.INVISIBLE

        mJob = Job()

        val styleList = mutableListOf<String>()
        val styleNameList = mutableListOf<String>()
        styleList.add("none.png")
        styleNameList.add("None")
        for (i in 1..26) {
            styleList.add("style$i.jpg")
            styleNameList.add("Style $i")
        }

        val borderDrawable = ContextCompat.getDrawable(this, R.drawable.image_border)
        val stylesRecyclerViewAdapter = StylesRecyclerViewAdapter(borderDrawable!!)
        stylesRecyclerViewAdapter.setStylesList(styleList, styleNameList)
        stylesRecyclerView.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = stylesRecyclerViewAdapter
        }
        LinearSnapHelper().attachToRecyclerView(stylesRecyclerView)

        styleTransferModel = StyleTransferModel(this)

        runWithPermissions(
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        ) {
            selectImageButton.setOnClickListener {
                CropImage.activity()
                    .setGuidelines(CropImageView.Guidelines.ON)
                    .setAspectRatio(1, 1)
                    .start(this)
            }
        }

        saveImageButton.setOnClickListener {
            if (!isModelRunning) {
                val imgDrawable = outputImage.drawable
                if (imgDrawable != null) {
                    val imgTemp = outputImage.drawable as BitmapDrawable
                    val imgBitmap = imgTemp.bitmap
                    saveImage(imgBitmap, this)
                } else {
                    Toast.makeText(this, "Output image is empty", Toast.LENGTH_SHORT).show()
                }
            }
        }

        stylesRecyclerView.addOnItemClickListener(object : OnItemClickListener {
            override fun onItemClicked(position: Int, view: View) {
                if (!isModelRunning) {
                    if (contentImage != null) {
                        isModelRunning = true
                        stylesRecyclerViewAdapter.notifyItemChanged(stylesRecyclerViewAdapter.selectedPos)
                        stylesRecyclerViewAdapter.selectedPos = position
                        stylesRecyclerViewAdapter.notifyItemChanged(stylesRecyclerViewAdapter.selectedPos)
                        if (position == 0) {
                            outputImage.setImageBitmap(contentImage)
                            isModelRunning = false
                        } else {
                            launch {
                                withContext(Dispatchers.IO) {
                                    withContext(Dispatchers.Main) {
                                        outputImage.visibility = View.INVISIBLE
                                        progressBar.visibility = View.VISIBLE
                                    }
                                    styleTransferModel.setUpFilter(styleList[position])
                                    val img = styleTransferModel.styleImage(contentImage!!)
                                    withContext(Dispatchers.Main) {
                                        progressBar.visibility = View.INVISIBLE
                                        outputImage.visibility = View.VISIBLE
                                        outputImage.setImageBitmap(img)
                                        isModelRunning = false
                                    }
                                }
                            }
                        }
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Select an image first",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Log.d("esh", "Model Running")
                }
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            val result = CropImage.getActivityResult(data)
            if (resultCode == Activity.RESULT_OK) {
                val uri = result.uri
                val imageStream = contentResolver.openInputStream(uri)
                contentImage = BitmapFactory.decodeStream(imageStream)
                outputImage.setImageBitmap(contentImage)
                Log.d("esh", "Images selected")
            } else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
                Log.d("esh", "Error ${result.error}")
            }
        }
    }

    private fun saveImage(imgBitmap: Bitmap, context: Context) {
        try {
            val pathStr =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
            val path = File(pathStr)
            if (!path.isDirectory || !path.exists()) {
                path.mkdirs()
            }
            val outFile = File(path, "${UUID.randomUUID()}.png")
            val stream = FileOutputStream(outFile)
            imgBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.flush()
            stream.close()
            Toast.makeText(context, "Image saved in Downloads", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.d("esh", "File error $e")
            Toast.makeText(context, "Could not be saved", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        styleTransferModel.close()
        mJob.cancel()
    }
}