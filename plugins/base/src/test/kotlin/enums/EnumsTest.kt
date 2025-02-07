package enums

import matchers.content.*
import org.jetbrains.dokka.pages.*
import org.jetbrains.dokka.base.testApi.testRunner.BaseAbstractTest
import org.jetbrains.dokka.model.*
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EnumsTest : BaseAbstractTest() {

    @Test
    fun basicEnum() {
        val configuration = dokkaConfiguration {
            sourceSets {
                sourceSet {
                    sourceRoots = listOf("src/")
                }
            }
        }

        testInline(
            """
            |/src/main/kotlin/basic/Test.kt
            |package enums
            |
            |enum class Test {
            |   E1,
            |   E2
            |}
        """.trimMargin(),
            configuration
        ) {
            pagesGenerationStage = {
                val map = it.getClasslikeToMemberMap()
                val test = map.filterKeys { it.name == "Test" }.values.firstOrNull()
                assertTrue(test != null) { "Test not found" }
                assertTrue(test!!.any { it.name == "E1" } && test.any { it.name == "E2" }) { "Enum entries missing in parent" }
            }
        }
    }

    @Test
    fun enumWithCompanion() {
        val configuration = dokkaConfiguration {
            sourceSets {
                sourceSet {
                    sourceRoots = listOf("src/")
                }
            }
        }

        testInline(
            """
            |/src/main/kotlin/basic/Test.kt
            |package enums
            |
            |enum class Test {
            |   E1,
            |   E2;
            |   companion object {}
            |}
        """.trimMargin(),
            configuration
        ) {
            documentablesTransformationStage = { m ->
                m.packages.let { p ->
                    assertTrue(p.isNotEmpty(), "Package list cannot be empty")
                    p.first().classlikes.let { c ->
                        assertTrue(c.isNotEmpty(), "Classlikes list cannot be empty")

                        val enum = c.first() as DEnum
                        assertEquals(enum.name, "Test")
                        assertEquals(enum.entries.count(), 2)
                        assertNotNull(enum.companion)
                    }
                }
            }
            pagesGenerationStage = { module ->
                val map = module.getClasslikeToMemberMap()
                val test = map.filterKeys { it.name == "Test" }.values.firstOrNull()
                assertNotNull(test, "Test not found")
                assertTrue(test!!.any { it.name == "E1" } && test.any { it.name == "E2" }) { "Enum entries missing in parent" }
            }
        }
    }

    @Test
    fun enumWithConstructor() {
        val configuration = dokkaConfiguration {
            sourceSets {
                sourceSet {
                    sourceRoots = listOf("src/")
                }
            }
        }

        testInline(
            """
            |/src/main/kotlin/basic/Test.kt
            |package enums
            |
            |
            |enum class Test(name: String, index: Int, excluded: Boolean) {
            |   E1("e1", 1, true),
            |   E2("e2", 2, false);
            |}
        """.trimMargin(),
            configuration
        ) {
            documentablesTransformationStage = { m ->
                m.packages.let { p ->
                    p.first().classlikes.let { c ->
                        val enum = c.first() as DEnum
                        val (first, second) = enum.entries.sortedBy { it.name }

                        assertEquals(1, first.extra.allOfType<ConstructorValues>().size)
                        assertEquals(1, second.extra.allOfType<ConstructorValues>().size)
                        assertEquals(listOf(StringConstant("e1"), IntegerConstant(1), BooleanConstant(true)), first.extra.allOfType<ConstructorValues>().first().values.values.first())
                        assertEquals(listOf(StringConstant("e2"), IntegerConstant(2), BooleanConstant(false)), second.extra.allOfType<ConstructorValues>().first().values.values.first())
                    }
                }
            }
            pagesGenerationStage = { module ->
                val entryPage = module.dfs { it.name == "E1" } as ClasslikePageNode
                val signaturePart = (entryPage.content.dfs {
                    it is ContentGroup && it.dci.toString() == "[enums/Test.E1///PointingToDeclaration/][Symbol]"
                } as ContentGroup)
                assertEquals("(\"e1\", 1, true)", signaturePart.constructorSignature())
            }
        }
    }

    @Test
    fun enumWithMethods() {
        val configuration = dokkaConfiguration {
            sourceSets {
                sourceSet {
                    sourceRoots = listOf("src/")
                }
            }
        }

        testInline(
            """
            |/src/main/kotlin/basic/Test.kt
            |package enums
            |
            |
            |interface Sample {
            |    fun toBeImplemented(): String
            |}
            |
            |enum class Test: Sample {
            |    E1 {
            |        override fun toBeImplemented(): String = "e1"
            |    }
            |}
        """.trimMargin(),
            configuration
        ) {
            documentablesTransformationStage = { m ->
                m.packages.let { p ->
                    p.first().classlikes.let { c ->
                        val enum = c.first { it is DEnum } as DEnum
                        val first = enum.entries.first()

                        assertEquals(1, first.extra.allOfType<ConstructorValues>().size)
                        assertEquals(emptyList<String>(), first.extra.allOfType<ConstructorValues>().first().values.values.first())
                        assertNotNull(first.functions.find { it.name == "toBeImplemented" })
                    }
                }
            }
        }
    }

    @Test
    fun enumWithAnnotationsOnEntries(){
        val configuration = dokkaConfiguration {
            sourceSets {
                sourceSet {
                    sourceRoots = listOf("src/")
                }
            }
        }

        testInline(
            """
            |/src/main/kotlin/basic/Test.kt
            |package enums
            |
            |enum class Test {
            |    /**
            |       Sample docs for E1
            |    **/
            |    @SinceKotlin("1.3") // This annotation is transparent due to lack of @MustBeDocumented annotation
            |    E1
            |}
        """.trimMargin(),
            configuration
        ) {
            pagesTransformationStage = { m ->
                val entryNode = m.children.first { it.name == "enums" }.children.first { it.name == "Test" }.children.firstIsInstance<ClasslikePageNode>()
                val signature = (entryNode.content as ContentGroup).dfs { it is ContentGroup && it.dci.toString() == "[enums/Test.E1///PointingToDeclaration/][Cover]" } as ContentGroup

                signature.assertNode {
                    header(1) { +"E1" }
                    platformHinted {
                        group {
                            group {
                                link { +"E1" }
                                +"()"
                            }
                        }
                        group {
                            group {
                                group {
                                    +"Sample docs for E1"
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    fun RootPageNode.getClasslikeToMemberMap() =
        this.parentMap.filterValues { it is ClasslikePageNode }.entries.groupBy({ it.value }) { it.key }

    private fun ContentGroup.constructorSignature(): String =
        (children.single() as ContentGroup).children.drop(1).joinToString(separator = "") { (it as ContentText).text }
}
