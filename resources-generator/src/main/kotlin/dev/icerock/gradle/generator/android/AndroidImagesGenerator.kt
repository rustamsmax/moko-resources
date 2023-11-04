/*
 * Copyright 2020 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.icerock.gradle.generator.android

import com.android.ide.common.vectordrawable.Svg2Vector
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.KModifier
import dev.icerock.gradle.generator.ImagesGenerator
import dev.icerock.gradle.generator.NOPObjectBodyExtendable
import dev.icerock.gradle.generator.ObjectBodyExtendable
import dev.icerock.gradle.utils.svg
import org.gradle.api.file.FileTree
import org.gradle.api.logging.Logger
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Path
import kotlin.reflect.full.functions

class AndroidImagesGenerator(
    inputFileTree: FileTree,
    private val getAndroidRClassPackage: () -> String,
    private val convertSvgToAndroidDrawables: Boolean,
    private val logger: Logger
) : ImagesGenerator(inputFileTree), ObjectBodyExtendable by NOPObjectBodyExtendable() {
    override fun getClassModifiers(): Array<KModifier> = arrayOf(KModifier.ACTUAL)

    override fun getPropertyModifiers(): Array<KModifier> = arrayOf(KModifier.ACTUAL)

    override fun getPropertyInitializer(fileName: String): CodeBlock {
        val processedKey = processKey(fileName.substringBefore("."))
        return CodeBlock.of("ImageResource(R.drawable.%L)", processedKey)
    }

    override fun getImports(): List<ClassName> = listOf(
        ClassName(getAndroidRClassPackage(), "R")
    )

    override fun generateResources(
        resourcesGenerationDir: File,
        keyFileMap: Map<String, List<File>>
    ) {
        keyFileMap.flatMap { (key, files) ->
            files.map { key to it }
        }.forEach { (key, file) ->
            val scale = file.nameWithoutExtension.substringAfter("@").substringBefore("x")
            val drawableDirName = "drawable" + when (scale) {
                "0.75" -> "-ldpi"
                "1" -> "-mdpi"
                "1.5" -> "-hdpi"
                "2" -> "-xhdpi"
                "3" -> "-xxhdpi"
                "4" -> "-xxxhdpi"
                else -> {
                    if (file.svg) {
                        ""
                    } else {
                        logger.warn("ignore $file - unknown scale ($scale)")
                        return@forEach
                    }
                }
            }

            val drawableDir = File(resourcesGenerationDir, drawableDirName)
            val processedKey = processKey(key)

            val resourceExtension = if (file.svg) "xml" else file.extension
            val resourceFile = File(drawableDir, "$processedKey.$resourceExtension")
            if (file.svg && convertSvgToAndroidDrawables) {
                parseSvgToVectorDrawable(file, resourceFile)
            } else {
                file.copyTo(resourceFile)
            }
        }
    }

    private fun parseSvgToVectorDrawable(svgFile: File, vectorDrawableFile: File) {
        try {
            vectorDrawableFile.parentFile.mkdirs()
            vectorDrawableFile.createNewFile()
            FileOutputStream(vectorDrawableFile, false).use { os ->
                parseSvgToXml(svgFile, os)
                    .takeIf { it.isNotEmpty() }
                    ?.let { error -> logger.warn("parse from $svgFile to xml:\n$error") }
            }
        } catch (e: IOException) {
            logger.error("parse from $svgFile to xml error", e)
        }
    }

    private fun parseSvgToXml(file: File, os: OutputStream): String {
        return try {
            Svg2Vector.parseSvgToXml(Path.of(file.absolutePath), os)
        } catch (e: NoSuchMethodError) {
            logger.debug(
                buildString {
                    append("Not found parseSvgToXml function with Path parameter. ")
                    append("Fallback to parseSvgToXml function with File parameter.")
                },
                e
            )
            val parseSvgToXmlFunction = Svg2Vector::class.functions.first {
                // broken ktlint rule Indentation workaround
                if (it.name != "parseSvgToXml") return@first false
                if (it.parameters.size != 2) return@first false
                if (it.parameters[0].type.classifier != File::class) return@first false
                if (it.parameters[1].type.classifier != OutputStream::class) return@first false
                return@first true
            }
            return parseSvgToXmlFunction.call(file, os) as String
        }
    }

    private fun processKey(key: String): String {
        return key.lowercase()
    }
}
