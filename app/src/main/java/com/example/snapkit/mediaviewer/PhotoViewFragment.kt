package com.example.snapkit.mediaviewer

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.example.snapkit.databinding.FragmentPhotoViewBinding
import kotlin.math.absoluteValue


class PhotoViewFragment : Fragment() {
    private lateinit var photoViewBinding: FragmentPhotoViewBinding
    var filePath: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        photoViewBinding = FragmentPhotoViewBinding.inflate(inflater, container, false)
        return photoViewBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupPhotoView()
        super.onViewCreated(view, savedInstanceState)
    }

    /**
     * Set the image for PhotoView using Glide and the private filePath property.
     */
    private fun setupPhotoView() {
        val photoView = photoViewBinding.photoView
        // Disable paging if PhotoView is zoomed in using a listener.
        photoView.setOnScaleChangeListener { _, _, _ ->
            // Calculate if the value is close to the expected value.
            val isValueApproximate = { expectedValue: Float, value: Float ->
                // The max amount of error that is allowed.
                val errorMargin = 0.03f
                val difference = expectedValue - value
                difference.absoluteValue < errorMargin
            }
            val isZoomed = isValueApproximate(photoView.minimumScale, photoView.scale)
            photoView.setAllowParentInterceptOnEdge(isZoomed)
        }
        // Load image into PhotoView
        filePath?.let {
            // Set transition name
            ViewCompat.setTransitionName(photoView, filePath)
            // Load image with glide
            Glide.with(photoView.context)
                .load(filePath)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>?,
                        isFirstResource: Boolean
                    ): Boolean {
                        parentFragment?.startPostponedEnterTransition()
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable?,
                        model: Any?,
                        target: Target<Drawable>?,
                        dataSource: DataSource?,
                        isFirstResource: Boolean
                    ): Boolean {
                        parentFragment?.startPostponedEnterTransition()
                        return false
                    }
                })
                .into(photoView)
        }
    }
}