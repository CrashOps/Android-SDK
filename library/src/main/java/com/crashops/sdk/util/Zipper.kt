package com.crashops.sdk.util

import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class Zipper {
    companion object {
        fun zipFolder(sourceDirectory: File, toZipFile: File): Boolean {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(toZipFile.path))).use {
                it.use {
                    zipFiles(it, sourceDirectory, "")
                }
            }

            return toZipFile.exists()
        }

        private fun zipFiles(zipOut: ZipOutputStream, sourceFile: File, parentDirPath: String) {
            sourceFile.listFiles()?.let { filesList ->
                val data = ByteArray(2048)

                for (f in filesList) {
                    if (f.path.endsWith("zip")) {
                        // Skip all zip files
                        continue
                    }

                    if (f.isDirectory) {
                        val entry = ZipEntry(f.name + File.separator)
                        entry.time = f.lastModified()
                        entry.isDirectory
                        entry.size = f.length()

                        SdkLogger.log("zip", "Adding Directory: " + f.name)
                        zipOut.putNextEntry(entry)

                        //Call recursively to add files within this directory
                        zipFiles(zipOut, f, f.name)
                    } else {
                        zipOneFile(zipOut, f, parentDirPath, data)
                    }
                }
            }
        }

        fun zipIt(fileToCompress: File): File? {
            val zipped = File(fileToCompress.parentFile, fileToCompress.name.replace(".log", ".zip"))

            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipped))).use { out ->
                FileInputStream(fileToCompress).use { fi ->
                    BufferedInputStream(fi).use { origin ->
                        val entry = ZipEntry(fileToCompress.name)
                        out.putNextEntry(entry)
                        origin.copyTo(out, 1024)
                    }
                }
            }

            return if (zipped.exists()) {
                zipped
            } else {
                null
            }
        }

        private fun zipOneFile(zipOut: ZipOutputStream, f: File, parentDirPath: String, data: ByteArray) {
            if (!f.name.contains(".zip")) { //If folder contains a file with extension ".zip", skip it
                FileInputStream(f).use { fi ->
                    BufferedInputStream(fi).use { origin ->
                        val path = parentDirPath + File.separator + f.name
                        //AppLogger.log("zip", "Adding file: $path")
                        val entry = ZipEntry(path)
                        entry.time = f.lastModified()
                        entry.isDirectory
                        entry.size = f.length()
                        zipOut.putNextEntry(entry)
                        while (true) {
                            val readBytes = origin.read(data)
                            if (readBytes == -1) {
                                break
                            }
                            zipOut.write(data, 0, readBytes)
                        }
                    }
                }
            } else {
                zipOut.closeEntry()
                zipOut.close()
            }
        }
    }
}