package org.dp.facedetection

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.zhihu.matisse.Matisse
import com.zhihu.matisse.MimeType
import com.zhihu.matisse.engine.impl.PicassoEngine
import com.zhihu.matisse.internal.entity.CaptureStrategy
import org.luban.Luban
import org.luban.OnCompressListener
import org.opencv.android.Utils
import org.opencv.core.MatOfRect
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.text.DecimalFormat

class MainActivity : AppCompatActivity() {

    private val PERMISSION_REQUEST_CODE = 551

    private lateinit var tvMessage: TextView
    private lateinit var btTakePhoto: Button
    private lateinit var ivImage: ImageView

    private val REQUEST_PERMISSION =
        arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

    private val REQUEST_CODE_CHOOSE = 1001

    lateinit var selectedPath: String

    lateinit var faceDetect: FaceDetect

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        faceDetect = FaceDetect()
        tvMessage = findViewById(R.id.textView)
        ivImage = findViewById(R.id.imageView)
        btTakePhoto = findViewById(R.id.btPhoto)
        btTakePhoto.setOnClickListener {
            if (checkPermission()) {
                pickPic()
            }
        }
    }

    private fun pickPic() {
        Matisse.from(this)
            .choose(MimeType.ofImage())
            .countable(true)
            .capture(true)
            .captureStrategy(
                CaptureStrategy(
                    true,
                    "org.dp.facedetection.fileprovider",
                    "facedetection"
                )
            )
            .maxSelectable(1)
            .imageEngine(PicassoEngine())
            .theme(R.style.Matisse_Dracula)
            .forResult(REQUEST_CODE_CHOOSE)
    }

    private fun checkPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        } else {
            if (this.isFinishing) {
                return false
            }
            return if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    REQUEST_PERMISSION,
                    PERMISSION_REQUEST_CODE
                )
                false
            } else {
                true
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.size == 2
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && grantResults[1] == PackageManager.PERMISSION_GRANTED
            ) {
                pickPic()
            } else {
                Toast.makeText(this, "未授权，无法选择图片", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == REQUEST_CODE_CHOOSE) {
            selectedPath = Matisse.obtainPathResult(data)[0]
            compressPic(selectedPath)
        }
    }

    private fun compressPic(picPath: String) {
        Luban.with(this)
            .load(picPath)
            .filter { path -> !(TextUtils.isEmpty(path) || path.toLowerCase().endsWith(".gif")) }
            .setCompressListener(object : OnCompressListener {
                override fun onStart() {
                }

                override fun onSuccess(file: File?) {
                    file?.let {
                        fileFaceDetection(it)
                    }
                }

                override fun onError(e: Throwable?) {
                    Toast.makeText(
                        this@MainActivity,
                        "图片压缩失败：" + e.toString(),
                        Toast.LENGTH_SHORT
                    )
                        .show()
                }
            }).launch()
    }

    /**
     * File to Bitmap
     */
    private fun getBitmapFromFile(file: File): Bitmap? {
        var image: Bitmap? = null
        try {
            val stream = FileInputStream(file)
            image = BitmapFactory.decodeStream(stream)
            stream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            return image
        }
    }

    fun fileFaceDetection(file: File) {
        val bmp = getBitmapFromFile(file)
        var str = "image size = ${bmp?.width}x${bmp?.height} ${getFileSize(file)}\n"
        ivImage.setImageBitmap(bmp)
        val mat = MatOfRect()
        val bmp2 = bmp?.copy(bmp.config, true)
        Utils.bitmapToMat(bmp, mat)
        val FACE_RECT_COLOR = Scalar(255.0, 0.0, 0.0, 0.0)
        val startTime = System.currentTimeMillis()
        val facesArray = faceDetect.doFaceDetect(file)
        str += "face num = ${facesArray.size}\n"
        for (face in facesArray) {
            str += "confidence = ${face.faceConfidence} x = ${face.faceRect.x} y = ${face.faceRect.y} width = ${face.faceRect.width} height = ${face.faceRect.height}\n"
            val start = Point(face.faceRect.x.toDouble(), face.faceRect.y.toDouble())
            val end = Point(
                face.faceRect.x.toDouble() + face.faceRect.width,
                face.faceRect.y.toDouble() + face.faceRect.height
            )
            Imgproc.rectangle(mat, start, end, FACE_RECT_COLOR)
        }

        str += "detectTime = ${System.currentTimeMillis() - startTime}ms\n"
        Utils.matToBitmap(mat, bmp2)
        ivImage.setImageBitmap(bmp2)
        tvMessage.text = str
    }

    private fun getFileSize(file: File): String {
        val size: String
        if (file.exists() && file.isFile) {
            val fileS = file.length()
            val df = DecimalFormat("#.00")
            if (fileS < 1024) {
                size = df.format(fileS.toDouble()) + "BT"
            } else if (fileS < 1048576) {
                size = df.format(fileS.toDouble() / 1024) + "KB"
            } else if (fileS < 1073741824) {
                size = df.format(fileS.toDouble() / 1048576) + "MB"
            } else {
                size = df.format(fileS.toDouble() / 1073741824) + "GB"
            }
        } else if (file.exists() && file.isDirectory) {
            size = ""
        } else {
            size = "0BT"
        }
        return size
    }
}