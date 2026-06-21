package com.abuzahra.admin.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

/**
 * Base fragment providing common lifecycle helpers. All dashboard fragments
 * extend this so they share a consistent backfill.
 */
abstract class BaseFragment : Fragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Fragments can override to add behaviour
    }

    /** Tag used by debugging / FragmentManager logs. */
    open val logTag: String get() = this::class.java.simpleName
}
