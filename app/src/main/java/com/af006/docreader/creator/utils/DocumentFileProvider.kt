package com.af010.creator.docreader.utils

import androidx.core.content.FileProvider

/** Own subclass so it never clashes with a library's FileProvider in the merged manifest. */
class DocumentFileProvider : FileProvider()
