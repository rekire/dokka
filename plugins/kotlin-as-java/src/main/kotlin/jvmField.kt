package org.jetbrains.dokka.kotlinAsJava

import org.jetbrains.dokka.model.Annotations
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.properties.WithExtraProperties

internal fun WithExtraProperties<out Documentable>.jvmField(): Annotations.Annotation? =
    extra[Annotations]?.directAnnotations?.entries?.mapNotNull { (_, annotations) -> annotations.jvmFieldAnnotation() }?.firstOrNull()

internal fun List<Annotations.Annotation>.jvmFieldAnnotation(): Annotations.Annotation? =
    firstOrNull { it.dri.packageName == "kotlin.jvm" && it.dri.classNames == "JvmField" }