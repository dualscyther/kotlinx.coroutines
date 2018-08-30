/*
 * Copyright 2016-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines.exceptions

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import org.junit.Test
import java.io.*
import java.util.concurrent.*
import kotlin.coroutines.*
import kotlin.test.*

class StackTraceRecoveryTest : TestBase() {

    @Test
    fun testAsync() = runTest {
        val deferred = async(coroutineContext) {
            throw ExecutionException(null)
        }

        nestedMethod(deferred, "testAsync")
        deferred.join()
    }

    @Test
    fun testCompletedAsync() = runTest {
        val deferred = async(coroutineContext) {
            throw ExecutionException(null)
        }
        deferred.join()

        nestedMethod(deferred, "testCompletedAsync")
    }

    private suspend fun nestedMethod(deferred: Deferred<Nothing>, testName: String) {
        oneMoreNestedMethod(deferred, testName)
        assertTrue(true) // Prevent tail-call
    }

    private suspend fun oneMoreNestedMethod(deferred: Deferred<Nothing>, testName: String) {
        try {
            deferred.await()
        } catch (e: ExecutionException) {
            checkStackTrace(e, testName, testName, "nestedMethod", "oneMoreNestedMethod")
        }
    }

    @Test
    fun testReceiveFromChannel() = runTest {
        val channel = Channel<Int>(1)
        val job = launch(coroutineContext) {
            expect(2)
            channel.close(IllegalArgumentException())
        }

        expect(1)
        channelNestedMethod(channel, "testReceiveFromChannel")
        expect(3)
        job.join()
        finish(4)
    }

    @Test
    fun testReceiveFromClosedChannel() = runTest {
        val channel = Channel<Int>(1)
        channel.close(IllegalArgumentException())
        channelNestedMethod(channel, "testReceiveFromClosedChannel")
    }

    private suspend fun channelNestedMethod(channel: Channel<Int>, testName: String) {
        try {
            channel.receive()
            expectUnreached()
        } catch (e: IllegalArgumentException) {
            checkStackTrace(e, testName, "channelNestedMethod")
        }
    }

    private fun checkStackTrace(t: Throwable, vararg expectedSubstrings: String) {
        val stacktrace = toStackTrace(t)
        println(stacktrace)
        expectedSubstrings.forEach {
            assertTrue(
                stacktrace.contains(it),
                "Stacktrace doesn't contain $it: \n$stacktrace"
            )
        }
    }

    private fun toStackTrace(t: Throwable): String {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }

    @Test
    fun testFullStackTrace() = runTest {
        val deferred = async(coroutineContext) {
            val i: Any = 1
            i as Long
        }

        try {
            nestedMethod(deferred, 3)
        } catch (e: ClassCastException) {
            val stacktrace = toStackTrace(e)

            val expected = """
            java.lang.ClassCastException
            	at kotlinx.coroutines.DeferredCoroutine.await(Unknown Source)
            	at kotlinx.coroutines.exceptions.StackTraceRecoveryTest.throwingMethod(Unknown Source)
            	at kotlinx.coroutines.exceptions.StackTraceRecoveryTest.nestedMethod(Unknown Source)
            	at kotlinx.coroutines.exceptions.StackTraceRecoveryTest.nestedMethod(Unknown Source)
            	at kotlinx.coroutines.exceptions.StackTraceRecoveryTest.nestedMethod(Unknown Source)
            	at kotlinx.coroutines.exceptions.StackTraceRecoveryTest.testFullStackTrace(Unknown Source)
            	at kotlinx.coroutines.BlockingCoroutine.(Unknown Source)
            Caused by: java.lang.ClassCastException: java.lang.Integer cannot be cast to java.lang.Long
            	at kotlinx.coroutines.exceptions.StackTraceRecoveryTest${'$'}testFullStackTrace${'$'}1${'$'}deferred${'$'}1.invokeSuspend(StackTraceRecoveryTest.kt:101)
            	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:42)
            	at kotlinx.coroutines.DispatchedTask${'$'}DefaultImpls.run(Dispatched.kt:149)
            	at kotlinx.coroutines.DispatchedContinuation.run(Dispatched.kt:13)
            	at kotlinx.coroutines.EventLoopBase.processNextEvent(EventLoop.kt:132)
            	at kotlinx.coroutines.BlockingCoroutine.joinBlocking(Builders.kt:69)
            	at kotlinx.coroutines.BuildersKt__BuildersKt.runBlocking(Builders.kt:46)
            	at kotlinx.coroutines.BuildersKt.runBlocking(Unknown Source)
            	at kotlinx.coroutines.TestBase.runTest(TestBase.kt:132)
            	at kotlinx.coroutines.TestBase.runTest${'$'}default(TestBase.kt:19)
            	at kotlinx.coroutines.exceptions.StackTraceRecoveryTest.testFullStackTrace(StackTraceRecoveryTest.kt:98)
            """.trim()

            println(stacktrace)
            assertTrue(stacktrace.trim().startsWith(expected), stacktrace)
        }
    }

    private fun String.trim(): String {
        return trimIndent().replace(Regex(":[0-9]+"), "")
    }

    private suspend fun nestedMethod(deferred: Deferred<*>, depth: Int) {
        if (depth - 1 == 0) {
            throwingMethod(deferred)
            assertTrue(true) // tail-call
        } else {
            nestedMethod(deferred, depth - 1)
        }
    }

    private suspend fun throwingMethod(deferred: Deferred<*>) {
        deferred.await()
    }
}