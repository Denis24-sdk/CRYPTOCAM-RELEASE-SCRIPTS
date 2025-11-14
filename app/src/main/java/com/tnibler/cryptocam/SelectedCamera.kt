    package com.tnibler.cryptocam

    enum class SelectedCamera {
        FRONT, BACK;

        fun other() = when (this) {
            BACK -> FRONT
            FRONT -> BACK
        }
    }