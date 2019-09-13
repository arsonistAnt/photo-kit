package com.example.snapkit.mediaviewer

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.viewpager.widget.ViewPager
import com.example.snapkit.R
import com.example.snapkit.SharedGalleryViewModel
import com.example.snapkit.database.FavoritedImage
import com.example.snapkit.databinding.FragmentMediaViewPagerBinding
import com.example.snapkit.domain.ImageFile
import com.example.snapkit.utils.*
import java.io.File

// Store the page margin value (in dp)
private const val PAGE_MARGIN = 24

class MediaViewPagerFragment : Fragment() {
    private lateinit var binding: FragmentMediaViewPagerBinding
    private lateinit var sharedGallery: SharedGalleryViewModel
    private lateinit var mediaViewPager: MediaViewPager
    private lateinit var mediaViewModel: MediaViewModel
    private var sysWindowsVisible = true
    private val safeFragmentArgs: MediaViewPagerFragmentArgs by navArgs()
    private val permissions = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentMediaViewPagerBinding.inflate(inflater)
        sharedGallery = ViewModelProviders.of(requireActivity()).get(SharedGalleryViewModel::class.java)
        mediaViewModel = ViewModelProviders.of(requireActivity()).get(MediaViewModel::class.java)
        binding.viewModel = mediaViewModel
        initBottomNavBar()
        initMediaPager()
        initObserversShared()
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        ViewCompat.requestApplyInsets(binding.mediaMenuLayout)
        if (!hasPermissions(requireContext(), *permissions)) {
            val mediaDialog = getAlertDialog(requireContext())
            mediaDialog.setMessage(getString(R.string.storage_dialog_message))
            mediaDialog.setTitle(R.string.permissions_dialog_title)
            requestForPermissions(requireActivity(), mediaDialog, *permissions)
        } else {
            sharedGallery.updateImageFiles()
        }
    }

    /**
     * Setup basic configurations for the Media Viewpager.
     */
    private fun initMediaPager() {
        mediaViewPager = binding.mediaViewer
        mediaViewPager.pageMargin = PAGE_MARGIN.dp.toPx()
        // Set position of the pager to the one that the user clicked in the ThumbnailGalleryFragment.
        mediaViewPager.currentItem = safeFragmentArgs.clickPosition
        mediaViewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {}
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                handleHeartIcon(position)
            }
        })
    }

    /**
     * Handles how the Heart icon should be shown.
     *
     * @param position the position of the image file that will help conclude how the Heart icon should be shown
     */
    private fun handleHeartIcon(position: Int) {
        val currentImageFile = getImageFileFromAdapter(position)
        currentImageFile?.apply {
            val favoritesList = sharedGallery.favoritedImages.value
            val hearted = favoritesList?.contains(filePath)
            hearted?.apply {
                toggleHeartImageButton(hearted)
            }
        }
    }


    /**
     * Subscribe observer to the shared ViewModel data.
     */
    private fun initObserversShared() {
        sharedGallery.mediaFiles.observe(viewLifecycleOwner, Observer { imageList ->
            val mediaViewPager = binding.mediaViewer
            val mediaViewPagerAdapter = MediaViewPagerAdapter(imageList, childFragmentManager)
            mediaViewPager.adapter = mediaViewPagerAdapter
            mediaViewPager.currentItem = safeFragmentArgs.clickPosition
            mediaViewPager.onSingleTap {
                toggleSystemUI()
            }
        })

        sharedGallery.favoritedImages.observe(viewLifecycleOwner, Observer { favoriteList ->
            handleHeartIcon(mediaViewPager.currentItem)
        })

        mediaViewModel.navigateToGallery.observe(viewLifecycleOwner, Observer { shouldNavigate ->
            if (shouldNavigate) {
                val navController = findNavController()
                val actionToGallery =
                    MediaViewPagerFragmentDirections.actionMediaViewPagerFragmentToImageGalleryFragment()
                navController.navigate(actionToGallery)
                mediaViewModel.navigateToGalleryDone()
            }
        })

        mediaViewModel.sharePhoto.observe(viewLifecycleOwner, Observer { startShareIntent ->
            if (startShareIntent) {
                shareImageIntent()
                mediaViewModel.sharePhotoDone()
            }
        })

        mediaViewModel.hearted.observe(viewLifecycleOwner, Observer { heartButtonClicked ->
            if (heartButtonClicked) {
                val imageFile = getImageFileFromAdapter(mediaViewPager.currentItem)
                imageFile?.apply {
                    // By default ImageFile has its hearted member property to false initially, so inverting it will be fine.
                    val favoriteImagePath = FavoritedImage(filePath)
                    val favoritesList = sharedGallery.favoritedImages.value
                    val inFavorites = favoritesList?.contains(filePath)
                    inFavorites?.apply {
                        if (!inFavorites) {
                            // Will trigger the favoriteImages Observable -> calling toggleHeartImageButton()
                            sharedGallery.addToFavoritesDB(favoriteImagePath)
                        } else {
                            // Will trigger the favoriteImages Observable -> calling toggleHeartImageButton()
                            sharedGallery.removeFromFavoritesDB(favoriteImagePath)
                        }
                    }
                }
                mediaViewModel.heartButtonClickedDone()
            }
        })
    }

    /**
     * Toggle heart button drawable.
     *
     * @param hearted a boolean that determines which heart drawable should be shown.
     */
    private fun toggleHeartImageButton(hearted: Boolean) {
        if (hearted)
            binding.heartImageButton.setImageResource(R.drawable.ic_heart_filled_icon_temp)
        else
            binding.heartImageButton.setImageResource(R.drawable.ic_heart_border_icon_temp)
    }

    /**
     * Start an ACTION_SEND intent to share an image.
     */
    private fun shareImageIntent() {
        val filePathString = getImageFileFromAdapter(mediaViewPager.currentItem)?.filePath
        val imageUri =
            FileProvider.getUriForFile(requireContext(), "com.example.snapkit.fileprovider", File(filePathString))
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, imageUri)
            type = "image/*"
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        startActivity(Intent.createChooser(shareIntent, "Share Image to..."))
    }

    /**
     * Hide status bar and navigation bar.
     */
    private fun hideSysWindows(activity: Window) {
        activity.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE)
    }

    /**
     * Show status bar and navigation bar.
     */
    private fun showSysWindows(activity: Window) {
        activity.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    /**
     * Allow toggling of navigation and status bars with a single tap.
     */
    private fun toggleSystemUI() {
        val activityWindow = requireActivity().window
        sysWindowsVisible = if (sysWindowsVisible) {
            hideSysWindows(activityWindow)
            binding.mediaMenuLayout.visibility = View.GONE
            false
        } else {
            showSysWindows(activityWindow)
            binding.mediaMenuLayout.visibility = View.VISIBLE
            true
        }
    }

    /**
     * Set parameters for the bottom navigation bar.
     */
    private fun initBottomNavBar() {
        val menuLayout = binding.mediaMenuLayout
        val menuMarginParams = menuLayout.layoutParams as ViewGroup.MarginLayoutParams
        val bottomMargin = menuMarginParams.bottomMargin

        ViewCompat.setOnApplyWindowInsetsListener(menuLayout) { _, insets ->
            menuMarginParams.bottomMargin = bottomMargin + insets.systemWindowInsetBottom
            insets
        }
    }

    /**
     * Return an ImageFile from a given position for the MediaViewPager's adapter.
     *
     * @param position the position of the ImageFile list to obtain.
     * @return an ImageFile object.
     */
    private fun getImageFileFromAdapter(position: Int): ImageFile? {
        val adapter = mediaViewPager.adapter as MediaViewPagerAdapter?
        return adapter?.getImageFile(position)
    }
}