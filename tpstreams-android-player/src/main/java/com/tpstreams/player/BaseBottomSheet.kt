package com.tpstreams.player

import android.app.Dialog
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

abstract class BaseBottomSheet : BottomSheetDialogFragment() {

    override fun getTheme(): Int = R.style.BottomSheetDialogTheme

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val bottomSheet = bottomSheetDialog.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            
            bottomSheet?.let {
                setupBottomSheetBehavior(it)
                applyHorizontalMargins(it)
            }
        }
        
        return dialog
    }

    private fun setupBottomSheetBehavior(bottomSheet: FrameLayout) {
        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.skipCollapsed = true
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun applyHorizontalMargins(bottomSheet: FrameLayout) {
        val layoutParams = bottomSheet.layoutParams as? ViewGroup.MarginLayoutParams
        layoutParams?.let { params ->
            val horizontalMargin = resources.getDimensionPixelSize(R.dimen.bottom_sheet_horizontal_margin)
            params.leftMargin = horizontalMargin
            params.rightMargin = horizontalMargin
            bottomSheet.layoutParams = params
        }
    }
}
