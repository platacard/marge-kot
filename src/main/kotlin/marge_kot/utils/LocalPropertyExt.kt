package marge_kot.utils

import java.io.File
import java.io.FileInputStream
import java.util.Properties

fun getLocalProperty(key: String, file: String = "local.properties"): String {
  val props = Properties().apply {
    load(FileInputStream(File( "local.properties")))
  }
  return props.getProperty(key)
}