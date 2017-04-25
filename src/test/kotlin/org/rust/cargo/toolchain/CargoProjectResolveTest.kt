package org.rust.cargo.toolchain

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiRecursiveElementVisitor
import org.rust.FileTreeBuilder
import org.rust.TestProject
import org.rust.cargo.RustWithToolchainTestBase
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.CargoProjectWorkspaceService
import org.rust.cargo.project.workspace.cargoWorkspace
import org.rust.fileTree
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsPath
import org.rust.utils.fullyRefreshDirectory
import kotlin.system.measureTimeMillis

class CargoProjectResolveTest : RustWithToolchainTestBase() {

    override val dataPath: String = ""

    fun `test resolve external library`() = buildProject {
        toml("Cargo.toml", """
            [package]
            name = "intellij-rust-test"
            version = "0.1.0"
            authors = []

            [dependencies]
            rand = "=0.3.14"
    """)

        dir("src") {
            rust("main.rs", """
                extern crate rand;

                use rand::distributions;

                mod foo;

                fn main() {
                    let _ = distributions::normal::Normal::new(0.0, 1.0);
                }                         //^
            """)
        }
    }.checkReferenceIsResolved<RsPath>("src/main.rs")

    fun `test resolve local package`() = buildProject {
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"
            authors = []

            [dependencies]
            foo = { path = "./foo" }
        """)

        dir("src") {
            rust("main.rs", """
                extern crate foo;
                mod bar;

                fn main() {
                    foo::hello();
                }       //^
            """)

            rust("bar.rs", """
                use foo::hello;

                pub fn bar() {
                    hello();
                }   //^
            """)
        }

        dir("foo") {
            toml("Cargo.toml", """
                [package]
                name = "foo"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("lib.rs", """
                    pub fn hello() {}
                """)
            }
        }
    }.run {
        checkReferenceIsResolved<RsPath>("src/main.rs")
        checkReferenceIsResolved<RsPath>("src/bar.rs")
    }

    fun `test module relations`() = buildProject {
        toml("Cargo.toml", """
            [package]
            name = "mods"
            version = "0.1.0"
            authors = []

            [dependencies]
        """)

        dir("src") {
            rust("lib.rs", """
                mod foo;

                pub struct S;
            """)

            rust("foo.rs", """
                use S;
                  //^
            """)
        }
    }.checkReferenceIsResolved<RsPath>("src/foo.rs")

    fun `test kebab-case`() = buildProject {
        toml("Cargo.toml", """
            [package]
            name = "kebab-case"
            version = "0.1.0"
            authors = []

            [dependencies]
        """)

        dir("src") {
            rust("main.rs", """
                extern crate kebab_case;

                fn main() {
                    kebab_case::foo();
                }              //^
            """)

            rust("lib.rs", "pub fn foo() { }")
        }
    }.checkReferenceIsResolved<RsPath>("src/main.rs")

    fun `test case insensitive mods`() {
        if (!SystemInfo.isWindows) return
        buildProject {
            toml("Cargo.toml", """
                [package]
                name = "mods"
                version = "0.1.0"
                authors = []

                [dependencies]
            """)

            dir("src") {
                rust("lib.rs", "mod foo; mod bar;")
                rust("FOO.rs", "pub struct Spam;")
                rust("BAR.rs", """
                    use foo::Spam;
                             //^
                """)
            }
        }.checkReferenceIsResolved<RsPath>("src/BAR.rs")
    }

    // Test that we don't choke on winapi crate, which uses **A LOT** of
    // glob imports and is just **ENORMOUS**
    fun `test winapi torture`() = buildProject {
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"
            authors = []

            [dependencies]
            winapi = "0.2"
        """)

        dir("src") {
            rust("main.rs", """
                extern crate winapi;
                use winapi::*;

                fn main() {
                    let _ = foo;
                }          //^
            """)
        }
    }.checkReferenceIsResolved<RsPath>("src/main.rs", shouldNotResolve = true)

    fun `test multiversion crate resolve`() = buildProject {
        toml("Cargo.toml", """
            [workspace]
            members = ["hello"]
        """)
        dir("hello") {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                workspace = "../"

                [dependencies]
                rand = "=0.3.14"
                bar = { version = "7.0.0", path = "../bar" }
            """)
            dir("src") {
                rust("main.rs", """
                    extern crate rand;
                    extern crate bar;
                    fn main() {
                        let _ = rand::thread_rng();
                                     // ^
                    }
                """)
            }
        }
        dir("bar") {
            toml("Cargo.toml", """
                [package]
                name = "bar"
                version = "7.0.0"

                [dependencies]
                rand = { version = "54.0.0", path="../rand" }
            """)
            dir("src") {
                rust("lib.rs", """
                    extern crate rand;
                    fn bar() {
                        let _ = rand::thread_rng();
                                     // ^
                    }
                """)
            }
        }
        dir("rand") {
            toml("Cargo.toml", """
                [package]
                name = "rand"
                version = "54.0.0"
            """)
            dir("src") {
                rust("lib.rs", """
                    pub fn thread_rng() -> u32 { 42 }
            """)
            }
        }
    }.run {
        checkReferenceIsResolved<RsPath>("hello/src/main.rs", toCrate = "rand 0.3.14")
        checkReferenceIsResolved<RsPath>("bar/src/lib.rs", toCrate = "rand 54.0.0")
    }

    fun buildProject(builder: FileTreeBuilder.() -> Unit): TestProject =
        fileTree { builder() }.create(project, project.baseDir).apply {
            refreshWorkspace()
        }

    private fun refreshWorkspace() {
        CargoProjectWorkspaceService.getInstance(module).syncUpdate(module.project.toolchain!!)
        if (module.cargoWorkspace == null) {
            error("Failed to update a test Cargo project")
        }
    }


    @Suppress("unused")
    fun measureResolveTime() {
        val pathToCargoSource = "/home/user/examples/cargo"
        val base = openRealProject(pathToCargoSource)
        val toml = base.findFileByRelativePath("src/cargo/util/toml.rs")
            ?: error("failed to find toml file")
        val psiManager = PsiManager.getInstance(project)
        val psi = psiManager.findFile(toml) as RsFile

        val elapsed = measureTimeMillis {
            psi.accept(object : PsiRecursiveElementVisitor() {
                override fun visitElement(element: PsiElement) {
                    super.visitElement(element)
                    element.reference?.resolve()
                }
            })
        }

        println("elapsed = ${elapsed}ms")
    }

    @Suppress("unused")
    private fun openRealProject(path: String): VirtualFile {
        runWriteAction {
            VfsUtil.copyDirectory(
                this,
                LocalFileSystem.getInstance().findFileByPath(path)!!,
                project.baseDir,
                { true }
            )
            fullyRefreshDirectory(project.baseDir)
        }

        refreshWorkspace()
        return project.baseDir
    }
}
