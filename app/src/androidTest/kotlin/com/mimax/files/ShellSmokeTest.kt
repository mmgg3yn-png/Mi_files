package com.mimax.files

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mimax.files.termux.BootstrapInstaller
import com.mimax.files.termux.SelfTest
import com.mimax.files.termux.TermuxEnv
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real on-device tests. Run in CI on an x86_64 emulator (see .github/workflows/test.yml) or locally with
 * `gradle :app:connectedDebugAndroidTest -PwithX86=true` on any device/emulator that has a matching bootstrap asset.
 */
@RunWith(AndroidJUnit4::class)
class ShellSmokeTest {
    private lateinit var env: TermuxEnv

    @Before fun setup() {
        env = TermuxEnv(InstrumentationRegistry.getInstrumentation().targetContext)
        if (!env.isInstalled) BootstrapInstaller.install(env) { }
    }

    private fun check(cmd: String, timeout: Long = 60) {
        val r = SelfTest.exec(env, cmd, timeout)
        println("CMD: $cmd\n${r.output}")
        assertTrue("FAILED: $cmd\n${r.output}", r.ok)
    }

    @Test fun bashRuns() = check("echo hello | grep hello")
    @Test fun prefixIsAppPrefix() {
        check("test \"\$PREFIX\" = /data/data/com.mimax.files/files/usr")
        check("test \"\$HOME\" = /data/data/com.mimax.files/files/home")
        check("test \"\$TMPDIR\" = /data/data/com.mimax.files/files/usr/tmp")
    }
    @Test fun aptAndDpkgRun() { check("apt --version"); check("dpkg --version") }
    @Test fun pkgUpdateWorks() = check("pkg update -y", 600)
    @Test fun installAndRunPythonGit() {
        check("pkg install -y python git", 1800)
        check("python -c 'print(6*7)' | grep 42")
        check("git --version")
    }
    @Test fun installAndRunNodeSshZsh() {
        check("pkg install -y nodejs openssh zsh", 1800)
        check("node -e 'console.log(1+1)' | grep 2"); check("npm --version"); check("ssh -V"); check("zsh --version")
    }
    @Test fun dataPersistsInHome() {
        check("echo persist > \$HOME/.mi_persist_test")
        check("grep persist \$HOME/.mi_persist_test")
    }
}
