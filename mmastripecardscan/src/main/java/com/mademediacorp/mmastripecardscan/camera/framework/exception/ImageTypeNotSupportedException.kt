package com.mademediacorp.mmastripecardscan.camera.framework.exception

import androidx.annotation.RestrictTo
import java.lang.Exception

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class ImageTypeNotSupportedException(val imageType: Int) : Exception()
