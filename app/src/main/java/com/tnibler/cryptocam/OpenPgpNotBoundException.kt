package com.tnibler.cryptocam

import java.lang.RuntimeException

class OpenPgpNotBoundException(val what: String? = null) : RuntimeException(what) {

}
