    package com.tnibler.system

    enum class SelectedCamera {
        FRONT, BACK;

        fun other() = when (this) {
            BACK -> FRONT
            FRONT -> BACK
        }
    }