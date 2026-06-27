package com.wastickers.romantic.stickers.loveromance.ad_mob.util

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

fun<T> AppCompatActivity.collectOnLifecycle(flow: Flow<T>, state: Lifecycle.State, action: suspend (value: T) -> Unit): Job {
    return lifecycleScope.launch {
        repeatOnLifecycle(state) {
            flow.collect(action)
        }
    }
}

fun<T> AppCompatActivity.collectLatestOnLifecycle(flow: Flow<T>, state: Lifecycle.State, action: suspend (value: T) -> Unit): Job {
    return lifecycleScope.launch {
        repeatOnLifecycle(state) {
            flow.collectLatest(action)
        }
    }
}

fun<T> AppCompatActivity.collectOnResume(flow: Flow<T>, action: suspend (value: T) -> Unit): Job {
    return lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.RESUMED) {
            flow.collect(action)
        }
    }
}

fun<T> AppCompatActivity.collectLatestOnResume(flow: Flow<T>, action: suspend (value: T) -> Unit): Job {
    return lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.RESUMED) {
            flow.collectLatest(action)
        }
    }
}

fun<T> Fragment.collectOnLifecycle(flow: Flow<T>, state: Lifecycle.State, action: suspend (value: T) -> Unit): Job {
    return lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(state) {
            flow.collect(action)
        }
    }
}

fun<T> Fragment.collectLatestOnLifecycle(flow: Flow<T>, state: Lifecycle.State, action: suspend (value: T) -> Unit): Job {
    return lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(state) {
            flow.collectLatest(action)
        }
    }
}

fun<T> Fragment.collectOnResume(flow: Flow<T>, action: suspend (value: T) -> Unit): Job {
    return lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            flow.collect(action)
        }
    }
}

fun<T> Fragment.collectLatestOnResume(flow: Flow<T>, action: suspend (value: T) -> Unit): Job {
    return lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            flow.collectLatest(action)
        }
    }
}