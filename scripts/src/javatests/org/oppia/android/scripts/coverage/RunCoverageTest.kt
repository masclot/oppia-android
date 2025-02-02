package org.oppia.android.scripts.coverage

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.oppia.android.scripts.common.CommandExecutorImpl
import org.oppia.android.scripts.common.ScriptBackgroundCoroutineDispatcher
import org.oppia.android.scripts.testing.TestBazelWorkspace
import org.oppia.android.testing.assertThrows
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.util.concurrent.TimeUnit

/** Tests for [RunCoverage]. */
class RunCoverageTest {
  @field:[Rule JvmField] val tempFolder = TemporaryFolder()

  private val outContent: ByteArrayOutputStream = ByteArrayOutputStream()
  private val originalOut: PrintStream = System.out

  private val scriptBgDispatcher by lazy { ScriptBackgroundCoroutineDispatcher() }
  private val commandExecutor by lazy { CommandExecutorImpl(scriptBgDispatcher) }
  private val longCommandExecutor by lazy { initializeCommandExecutorWithLongProcessWaitTime() }

  private lateinit var testBazelWorkspace: TestBazelWorkspace
  private lateinit var sampleFilePath: String
  private lateinit var sampleMDOutputPath: String
  private lateinit var sampleHTMLOutputPath: String

  @Before
  fun setUp() {
    sampleFilePath = "/path/to/Sample.kt"
    sampleMDOutputPath = "${tempFolder.root}/coverage_reports/report.md"
    sampleHTMLOutputPath = "${tempFolder.root}/coverage_reports/report.html"
    testBazelWorkspace = TestBazelWorkspace(tempFolder)
    System.setOut(PrintStream(outContent))
  }

  @After
  fun tearDown() {
    System.setOut(originalOut)
    scriptBgDispatcher.close()
  }

  @Test
  fun testRunCoverage_invalidFile_throwsException() {
    testBazelWorkspace.initEmptyWorkspace()
    val exception = assertThrows<IllegalStateException>() {
      main(tempFolder.root.absolutePath, "file.kt")
    }

    assertThat(exception).hasMessageThat().contains("File doesn't exist")
  }

  @Test
  fun testRunCoverage_missingTestFileNotExempted_throwsException() {
    testBazelWorkspace.initEmptyWorkspace()
    val exception = assertThrows<IllegalStateException>() {
      val sampleFile = File(tempFolder.root.absolutePath, "file.kt")
      sampleFile.createNewFile()
      main(tempFolder.root.absolutePath, "file.kt")
    }

    assertThat(exception).hasMessageThat().contains("No appropriate test file found")
  }

  @Test
  fun testRunCoverage_testFileExempted_noCoverage() {
    val exemptedFilePath = "app/src/main/java/org/oppia/android/app/activity/ActivityComponent.kt"

    val result = RunCoverage(
      "${tempFolder.root}",
      exemptedFilePath,
      ReportFormat.MARKDOWN,
      sampleMDOutputPath,
      commandExecutor,
      scriptBgDispatcher
    ).execute()

    assertThat(result).isEqualTo(
      "This file is exempted from having a test file; skipping coverage check."
    )
  }

  @Test
  fun testRunCoverage_sampleTestsDefaultFormat_returnsCoverageData() {
    testBazelWorkspace.initEmptyWorkspace()

    val sourceContent =
      """
      package com.example
      
      class TwoSum {
      
          companion object {
              fun sumNumbers(a: Int, b: Int): Any {
                  return if (a == 0 && b == 0) {
                      "Both numbers are zero"
                  } else {
                      a + b
                  }
              }
          }
      }
      """.trimIndent()

    val testContent =
      """
      package com.example
      
      import org.junit.Assert.assertEquals
      import org.junit.Test
      
      class TwoSumTest {
      
          @Test
          fun testSumNumbers() {
              assertEquals(TwoSum.sumNumbers(0, 1), 1)
              assertEquals(TwoSum.sumNumbers(3, 4), 7)         
              assertEquals(TwoSum.sumNumbers(0, 0), "Both numbers are zero")
          }
      }
      """.trimIndent()

    testBazelWorkspace.addSourceAndTestFileWithContent(
      filename = "TwoSum",
      testFilename = "TwoSumTest",
      sourceContent = sourceContent,
      testContent = testContent,
      sourceSubpackage = "coverage/main/java/com/example",
      testSubpackage = "coverage/test/java/com/example"
    )

    main(
      "${tempFolder.root}",
      "coverage/main/java/com/example/TwoSum.kt",
    )

    val outputReportText = File(
      "${tempFolder.root}/coverage_reports/coverage/main/java/com/example/TwoSum/coverage.md"
    ).readText()

    val expectedResult =
      """
        ## Coverage Report
        
        - **Covered File:** coverage/main/java/com/example/TwoSum.kt
        - **Coverage percentage:** 75.00% covered
        - **Line coverage:** 3 / 4 lines covered
      """.trimIndent()

    assertThat(outputReportText).isEqualTo(expectedResult)
  }

  @Test
  fun testRunCoverage_sampleTestsMarkdownFormat_returnsCoverageData() {
    testBazelWorkspace.initEmptyWorkspace()

    val sourceContent =
      """
      package com.example
      
      class TwoSum {
      
          companion object {
              fun sumNumbers(a: Int, b: Int): Any {
                  return if (a == 0 && b == 0) {
                      "Both numbers are zero"
                  } else {
                      a + b
                  }
              }
          }
      }
      """.trimIndent()

    val testContent =
      """
      package com.example
      
      import org.junit.Assert.assertEquals
      import org.junit.Test
      
      class TwoSumTest {
      
          @Test
          fun testSumNumbers() {
              assertEquals(TwoSum.sumNumbers(0, 1), 1)
              assertEquals(TwoSum.sumNumbers(3, 4), 7)         
              assertEquals(TwoSum.sumNumbers(0, 0), "Both numbers are zero")
          }
      }
      """.trimIndent()

    testBazelWorkspace.addSourceAndTestFileWithContent(
      filename = "TwoSum",
      testFilename = "TwoSumTest",
      sourceContent = sourceContent,
      testContent = testContent,
      sourceSubpackage = "coverage/main/java/com/example",
      testSubpackage = "coverage/test/java/com/example"
    )

    RunCoverage(
      "${tempFolder.root}",
      "coverage/main/java/com/example/TwoSum.kt",
      ReportFormat.MARKDOWN,
      sampleMDOutputPath,
      longCommandExecutor,
      scriptBgDispatcher
    ).execute()

    val outputReportText = File(sampleMDOutputPath).readText()

    val expectedResult =
      """
        ## Coverage Report
        
        - **Covered File:** coverage/main/java/com/example/TwoSum.kt
        - **Coverage percentage:** 75.00% covered
        - **Line coverage:** 3 / 4 lines covered
      """.trimIndent()

    assertThat(outputReportText).isEqualTo(expectedResult)
  }

  @Test
  fun testRunCoverage_scriptTestsMarkdownFormat_returnsCoverageData() {
    testBazelWorkspace.initEmptyWorkspace()

    val sourceContent =
      """
      package com.example
      
      class TwoSum {
      
          companion object {
              fun sumNumbers(a: Int, b: Int): Any {
                  return if (a == 0 && b == 0) {
                      "Both numbers are zero"
                  } else {
                      a + b
                  }
              }
          }
      }
      """.trimIndent()

    val testContent =
      """
      package com.example
      
      import org.junit.Assert.assertEquals
      import org.junit.Test
      
      class TwoSumTest {
      
          @Test
          fun testSumNumbers() {
              assertEquals(TwoSum.sumNumbers(0, 1), 1)
              assertEquals(TwoSum.sumNumbers(3, 4), 7)         
              assertEquals(TwoSum.sumNumbers(0, 0), "Both numbers are zero")
          }
      }
      """.trimIndent()

    testBazelWorkspace.addSourceAndTestFileWithContent(
      filename = "TwoSum",
      testFilename = "TwoSumTest",
      sourceContent = sourceContent,
      testContent = testContent,
      sourceSubpackage = "scripts/java/com/example",
      testSubpackage = "scripts/javatests/com/example"
    )

    RunCoverage(
      "${tempFolder.root}",
      "scripts/java/com/example/TwoSum.kt",
      ReportFormat.MARKDOWN,
      sampleMDOutputPath,
      longCommandExecutor,
      scriptBgDispatcher
    ).execute()

    val outputReportText = File(sampleMDOutputPath).readText()

    val expectedResult =
      """
        ## Coverage Report
        
        - **Covered File:** scripts/java/com/example/TwoSum.kt
        - **Coverage percentage:** 75.00% covered
        - **Line coverage:** 3 / 4 lines covered
      """.trimIndent()

    assertThat(outputReportText).isEqualTo(expectedResult)
  }

  @Test
  fun testRunCoverage_appTestsMarkdownFormat_returnsCoverageData() {
    testBazelWorkspace.initEmptyWorkspace()

    val sourceContent =
      """
      package com.example
      
      class TwoSum {
      
          companion object {
              fun sumNumbers(a: Int, b: Int): Any {
                  return if (a == 0 && b == 0) {
                      "Both numbers are zero"
                  } else {
                      a + b
                  }
              }
          }
      }
      """.trimIndent()

    val testContent =
      """
      package com.example
      
      import org.junit.Assert.assertEquals
      import org.junit.Test
      
      class TwoSumTest {
      
          @Test
          fun testSumNumbers() {
              assertEquals(TwoSum.sumNumbers(0, 1), 1)
              assertEquals(TwoSum.sumNumbers(3, 4), 7)         
              assertEquals(TwoSum.sumNumbers(0, 0), "Both numbers are zero")
          }
      }
      """.trimIndent()

    testBazelWorkspace.addSourceAndTestFileWithContent(
      filename = "TwoSum",
      testFilename = "TwoSumTest",
      sourceContent = sourceContent,
      testContent = testContent,
      sourceSubpackage = "app/main/java/com/example",
      testSubpackage = "app/test/java/com/example"
    )

    RunCoverage(
      "${tempFolder.root}",
      "app/main/java/com/example/TwoSum.kt",
      ReportFormat.MARKDOWN,
      sampleMDOutputPath,
      longCommandExecutor,
      scriptBgDispatcher
    ).execute()

    val outputReportText = File(sampleMDOutputPath).readText()

    val expectedResult =
      """
        ## Coverage Report
        
        - **Covered File:** app/main/java/com/example/TwoSum.kt
        - **Coverage percentage:** 75.00% covered
        - **Line coverage:** 3 / 4 lines covered
      """.trimIndent()

    assertThat(outputReportText).isEqualTo(expectedResult)
  }

  @Test
  fun testRunCoverage_localTestsMarkdownFormat_returnsCoverageData() {
    testBazelWorkspace.initEmptyWorkspace()

    val sourceContent =
      """
      package com.example
      
      class TwoSum {
      
          companion object {
              fun sumNumbers(a: Int, b: Int): Any {
                  return if (a == 0 && b == 0) {
                      "Both numbers are zero"
                  } else {
                      a + b
                  }
              }
          }
      }
      """.trimIndent()

    val testContent =
      """
      package com.example
      
      import org.junit.Assert.assertEquals
      import org.junit.Test
      
      class TwoSumLocalTest {
      
          @Test
          fun testSumNumbers() {
              assertEquals(TwoSum.sumNumbers(0, 1), 1)
              assertEquals(TwoSum.sumNumbers(3, 4), 7)         
              assertEquals(TwoSum.sumNumbers(0, 0), "Both numbers are zero")
          }
      }
      """.trimIndent()

    testBazelWorkspace.addSourceAndTestFileWithContent(
      filename = "TwoSum",
      testFilename = "TwoSumLocalTest",
      sourceContent = sourceContent,
      testContent = testContent,
      sourceSubpackage = "app/main/java/com/example",
      testSubpackage = "app/test/java/com/example"
    )

    RunCoverage(
      "${tempFolder.root}",
      "app/main/java/com/example/TwoSum.kt",
      ReportFormat.MARKDOWN,
      sampleMDOutputPath,
      longCommandExecutor,
      scriptBgDispatcher
    ).execute()

    val outputReportText = File(sampleMDOutputPath).readText()

    val expectedResult =
      """
        ## Coverage Report
        
        - **Covered File:** app/main/java/com/example/TwoSum.kt
        - **Coverage percentage:** 75.00% covered
        - **Line coverage:** 3 / 4 lines covered
      """.trimIndent()

    assertThat(outputReportText).isEqualTo(expectedResult)
  }

  @Test
  fun testRunCoverage_sharedTestsMarkdownFormat_returnsCoverageData() {
    testBazelWorkspace.initEmptyWorkspace()

    val sourceContent =
      """
      package com.example
      
      class TwoSum {
      
          companion object {
              fun sumNumbers(a: Int, b: Int): Any {
                  return if (a == 0 && b == 0) {
                      "Both numbers are zero"
                  } else {
                      a + b
                  }
              }
          }
      }
      """.trimIndent()

    val testContent =
      """
      package com.example
      
      import org.junit.Assert.assertEquals
      import org.junit.Test
      
      class TwoSumTest {
      
          @Test
          fun testSumNumbers() {
              assertEquals(TwoSum.sumNumbers(0, 1), 1)
              assertEquals(TwoSum.sumNumbers(3, 4), 7)         
              assertEquals(TwoSum.sumNumbers(0, 0), "Both numbers are zero")
          }
      }
      """.trimIndent()

    testBazelWorkspace.addSourceAndTestFileWithContent(
      filename = "TwoSum",
      testFilename = "TwoSumTest",
      sourceContent = sourceContent,
      testContent = testContent,
      sourceSubpackage = "app/main/java/com/example",
      testSubpackage = "app/sharedTest/java/com/example"
    )

    RunCoverage(
      "${tempFolder.root}",
      "app/main/java/com/example/TwoSum.kt",
      ReportFormat.MARKDOWN,
      sampleMDOutputPath,
      longCommandExecutor,
      scriptBgDispatcher
    ).execute()

    val outputReportText = File(sampleMDOutputPath).readText()

    val expectedResult =
      """
        ## Coverage Report
        
        - **Covered File:** app/main/java/com/example/TwoSum.kt
        - **Coverage percentage:** 75.00% covered
        - **Line coverage:** 3 / 4 lines covered
      """.trimIndent()

    assertThat(outputReportText).isEqualTo(expectedResult)
  }

  @Test
  fun testRunCoverage_sharedAndLocalTestsMarkdownFormat_returnsCoverageData() {
    testBazelWorkspace.initEmptyWorkspace()

    val sourceContent =
      """
      package com.example
      
      class TwoSum {
      
          companion object {
              fun sumNumbers(a: Int, b: Int): Any {
                  return if (a == 0 && b == 0) {
                      "Both numbers are zero"
                  } else {
                      a + b
                  }
              }
          }
      }
      """.trimIndent()

    val testContentShared =
      """
      package com.example
      
      import org.junit.Assert.assertEquals
      import org.junit.Test
      
      class TwoSumTest {
      
          @Test
          fun testSumNumbers() {
              assertEquals(TwoSum.sumNumbers(0, 1), 1)
              assertEquals(TwoSum.sumNumbers(3, 4), 7)         
              assertEquals(TwoSum.sumNumbers(0, 0), "Both numbers are zero")
          }
      }
      """.trimIndent()

    val testContentLocal =
      """
      package com.example
      
      import org.junit.Assert.assertEquals
      import org.junit.Test
      
      class TwoSumLocalTest {
      
          @Test
          fun testSumNumbers() {
              assertEquals(TwoSum.sumNumbers(0, 1), 1)
              assertEquals(TwoSum.sumNumbers(3, 4), 7)         
              assertEquals(TwoSum.sumNumbers(0, 0), "Both numbers are zero")
          }
      }
      """.trimIndent()

    testBazelWorkspace.addMultiLevelSourceAndTestFileWithContent(
      filename = "TwoSum",
      sourceContent = sourceContent,
      testContentShared = testContentShared,
      testContentLocal = testContentLocal,
      subpackage = "app"
    )

    RunCoverage(
      "${tempFolder.root}",
      "app/main/java/com/example/TwoSum.kt",
      ReportFormat.MARKDOWN,
      sampleMDOutputPath,
      longCommandExecutor,
      scriptBgDispatcher
    ).execute()

    val outputReportText = File(sampleMDOutputPath).readText()

    val expectedResult =
      """
        ## Coverage Report
        
        - **Covered File:** app/main/java/com/example/TwoSum.kt
        - **Coverage percentage:** 75.00% covered
        - **Line coverage:** 3 / 4 lines covered
      """.trimIndent()

    assertThat(outputReportText).isEqualTo(expectedResult)
  }

  @Test
  fun testRunCoverage_sampleTestsHTMLFormat_returnsCoverageData() {
    testBazelWorkspace.initEmptyWorkspace()

    val sourceContent =
      """
      package com.example
      
      class TwoSum {
      
          companion object {
              fun sumNumbers(a: Int, b: Int): Any {
                  return if (a == 0 && b == 0) {
                      "Both numbers are zero"
                  } else {
                      a + b
                  }
              }
          }
      }
      """.trimIndent()

    val testContent =
      """
      package com.example
      
      import org.junit.Assert.assertEquals
      import org.junit.Test
      
      class TwoSumTest {
      
          @Test
          fun testSumNumbers() {
              assertEquals(TwoSum.sumNumbers(0, 1), 1)
              assertEquals(TwoSum.sumNumbers(3, 4), 7)         
              assertEquals(TwoSum.sumNumbers(0, 0), "Both numbers are zero")
          }
      }
      """.trimIndent()

    testBazelWorkspace.addSourceAndTestFileWithContent(
      filename = "TwoSum",
      testFilename = "TwoSumTest",
      sourceContent = sourceContent,
      testContent = testContent,
      sourceSubpackage = "coverage/main/java/com/example",
      testSubpackage = "coverage/test/java/com/example"
    )

    RunCoverage(
      "${tempFolder.root}",
      "coverage/main/java/com/example/TwoSum.kt",
      ReportFormat.HTML,
      sampleHTMLOutputPath,
      longCommandExecutor,
      scriptBgDispatcher
    ).execute()

    val outputReportText = File(sampleHTMLOutputPath).readText()

    val expectedResult =
      """
    <!DOCTYPE html>
    <html lang="en">
    <head>
      <meta charset="UTF-8">
      <meta name="viewport" content="width=device-width, initial-scale=1.0">
      <title>Coverage Report</title>
      <style>
        body {
            font-family: Arial, sans-serif;
            line-height: 1.6;
            padding: 20px;
        }
        table {
            width: 100%;
            border-collapse: collapse;
            margin-bottom: 20px;
        }
        th, td {
            padding: 8px;
            margin-left: 20px;
            text-align: left;
            border-bottom: 1px solid #fdfdfd;
        }
        .line-number-col {
            width: 2%;
        }
        .line-number-row {
            border-right: 1px dashed #000000
        }
        .source-code-col {
            width: 98%;
        }
        .covered-line, .not-covered-line, .uncovered-line {
            white-space: pre-wrap;
            word-wrap: break-word;
            box-sizing: border-box;
            border-radius: 4px;
            padding: 2px 8px 2px 4px;
            display: inline-block;
        }
        .covered-line {
            background-color: #c8e6c9; /* Light green */
        }
        .not-covered-line {
            background-color: #ffcdd2; /* Light red */
        }
        .uncovered-line {
            background-color: #f1f1f1; /* light gray */
        }
        .coverage-summary {
          margin-bottom: 20px;
        }
        h2 {
          text-align: center;
        }
        ul {
          list-style-type: none;
          padding: 0;
          text-align: center;
        }
        .summary-box {
          background-color: #f0f0f0;
          border: 1px solid #ccc;
          border-radius: 8px;
          padding: 10px;
          margin-bottom: 20px;
          display: flex;
          justify-content: space-between;
          align-items: flex-start;
        }
        .summary-left {
          text-align: left;
        }
        .summary-right {
          text-align: right;
        }
        .legend {
          display: flex;
          align-items: center;
        }
        .legend-item {
          width: 20px;
          height: 10px;
          margin-right: 5px;
          border-radius: 2px;
          display: inline-block;
        }
        .legend .covered {
          background-color: #c8e6c9; /* Light green */
        }
        .legend .not-covered {
          margin-left: 4px;
          background-color: #ffcdd2; /* Light red */
        }
        @media screen and (max-width: 768px) {
            body {
                padding: 10px;
            }
            table {
                width: auto;
            }
        }
      </style>
    </head>
    <body>
      <h2>Coverage Report</h2>
      <div class="summary-box">
        <div class="summary-left">
          <strong>Covered File:</strong> coverage/main/java/com/example/TwoSum.kt <br>
          <div class="legend">
            <div class="legend-item covered"></div>
            <span>Covered</span>
            <div class="legend-item not-covered"></div>
            <span>Uncovered</span>
          </div>
        </div>
        <div class="summary-right">
          <div><strong>Coverage percentage:</strong> 75.00%</div>
          <div><strong>Line coverage:</strong> 3 / 4 covered</div>
        </div>
      </div>
      <table>
        <thead>
          <tr>
            <th class="line-number-col">Line No</th>
            <th class="source-code-col">Source Code</th>
          </tr>
        </thead>
        <tbody><tr>
        <td class="line-number-row">   1</td>
        <td class="uncovered-line">package com.example</td>
    </tr><tr>
        <td class="line-number-row">   2</td>
        <td class="uncovered-line"></td>
    </tr><tr>
        <td class="line-number-row">   3</td>
        <td class="not-covered-line">class TwoSum {</td>
    </tr><tr>
        <td class="line-number-row">   4</td>
        <td class="uncovered-line"></td>
    </tr><tr>
        <td class="line-number-row">   5</td>
        <td class="uncovered-line">    companion object {</td>
    </tr><tr>
        <td class="line-number-row">   6</td>
        <td class="uncovered-line">        fun sumNumbers(a: Int, b: Int): Any {</td>
    </tr><tr>
        <td class="line-number-row">   7</td>
        <td class="covered-line">            return if (a == 0 && b == 0) {</td>
    </tr><tr>
        <td class="line-number-row">   8</td>
        <td class="covered-line">                "Both numbers are zero"</td>
    </tr><tr>
        <td class="line-number-row">   9</td>
        <td class="uncovered-line">            } else {</td>
    </tr><tr>
        <td class="line-number-row">  10</td>
        <td class="covered-line">                a + b</td>
    </tr><tr>
        <td class="line-number-row">  11</td>
        <td class="uncovered-line">            }</td>
    </tr><tr>
        <td class="line-number-row">  12</td>
        <td class="uncovered-line">        }</td>
    </tr><tr>
        <td class="line-number-row">  13</td>
        <td class="uncovered-line">    }</td>
    </tr><tr>
        <td class="line-number-row">  14</td>
        <td class="uncovered-line">}</td>
    </tr>    </tbody>
      </table>
    </body>
    </html>
      """.trimIndent()

    assertThat(outputReportText).isEqualTo(expectedResult)
  }

  @Test
  fun testRunCoverage_scriptTestsHTMLFormat_returnsCoverageData() {
    testBazelWorkspace.initEmptyWorkspace()

    val sourceContent =
      """
      package com.example
      
      class TwoSum {
      
          companion object {
              fun sumNumbers(a: Int, b: Int): Any {
                  return if (a == 0 && b == 0) {
                      "Both numbers are zero"
                  } else {
                      a + b
                  }
              }
          }
      }
      """.trimIndent()

    val testContent =
      """
      package com.example
      
      import org.junit.Assert.assertEquals
      import org.junit.Test
      
      class TwoSumTest {
      
          @Test
          fun testSumNumbers() {
              assertEquals(TwoSum.sumNumbers(0, 1), 1)
              assertEquals(TwoSum.sumNumbers(3, 4), 7)         
              assertEquals(TwoSum.sumNumbers(0, 0), "Both numbers are zero")
          }
      }
      """.trimIndent()

    testBazelWorkspace.addSourceAndTestFileWithContent(
      filename = "TwoSum",
      testFilename = "TwoSumTest",
      sourceContent = sourceContent,
      testContent = testContent,
      sourceSubpackage = "scripts/java/com/example",
      testSubpackage = "scripts/javatests/com/example"
    )

    RunCoverage(
      "${tempFolder.root}",
      "scripts/java/com/example/TwoSum.kt",
      ReportFormat.HTML,
      sampleHTMLOutputPath,
      longCommandExecutor,
      scriptBgDispatcher
    ).execute()

    val outputReportText = File(sampleHTMLOutputPath).readText()

    val expectedResult =
      """
    <!DOCTYPE html>
    <html lang="en">
    <head>
      <meta charset="UTF-8">
      <meta name="viewport" content="width=device-width, initial-scale=1.0">
      <title>Coverage Report</title>
      <style>
        body {
            font-family: Arial, sans-serif;
            line-height: 1.6;
            padding: 20px;
        }
        table {
            width: 100%;
            border-collapse: collapse;
            margin-bottom: 20px;
        }
        th, td {
            padding: 8px;
            margin-left: 20px;
            text-align: left;
            border-bottom: 1px solid #fdfdfd;
        }
        .line-number-col {
            width: 2%;
        }
        .line-number-row {
            border-right: 1px dashed #000000
        }
        .source-code-col {
            width: 98%;
        }
        .covered-line, .not-covered-line, .uncovered-line {
            white-space: pre-wrap;
            word-wrap: break-word;
            box-sizing: border-box;
            border-radius: 4px;
            padding: 2px 8px 2px 4px;
            display: inline-block;
        }
        .covered-line {
            background-color: #c8e6c9; /* Light green */
        }
        .not-covered-line {
            background-color: #ffcdd2; /* Light red */
        }
        .uncovered-line {
            background-color: #f1f1f1; /* light gray */
        }
        .coverage-summary {
          margin-bottom: 20px;
        }
        h2 {
          text-align: center;
        }
        ul {
          list-style-type: none;
          padding: 0;
          text-align: center;
        }
        .summary-box {
          background-color: #f0f0f0;
          border: 1px solid #ccc;
          border-radius: 8px;
          padding: 10px;
          margin-bottom: 20px;
          display: flex;
          justify-content: space-between;
          align-items: flex-start;
        }
        .summary-left {
          text-align: left;
        }
        .summary-right {
          text-align: right;
        }
        .legend {
          display: flex;
          align-items: center;
        }
        .legend-item {
          width: 20px;
          height: 10px;
          margin-right: 5px;
          border-radius: 2px;
          display: inline-block;
        }
        .legend .covered {
          background-color: #c8e6c9; /* Light green */
        }
        .legend .not-covered {
          margin-left: 4px;
          background-color: #ffcdd2; /* Light red */
        }
        @media screen and (max-width: 768px) {
            body {
                padding: 10px;
            }
            table {
                width: auto;
            }
        }
      </style>
    </head>
    <body>
      <h2>Coverage Report</h2>
      <div class="summary-box">
        <div class="summary-left">
          <strong>Covered File:</strong> scripts/java/com/example/TwoSum.kt <br>
          <div class="legend">
            <div class="legend-item covered"></div>
            <span>Covered</span>
            <div class="legend-item not-covered"></div>
            <span>Uncovered</span>
          </div>
        </div>
        <div class="summary-right">
          <div><strong>Coverage percentage:</strong> 75.00%</div>
          <div><strong>Line coverage:</strong> 3 / 4 covered</div>
        </div>
      </div>
      <table>
        <thead>
          <tr>
            <th class="line-number-col">Line No</th>
            <th class="source-code-col">Source Code</th>
          </tr>
        </thead>
        <tbody><tr>
        <td class="line-number-row">   1</td>
        <td class="uncovered-line">package com.example</td>
    </tr><tr>
        <td class="line-number-row">   2</td>
        <td class="uncovered-line"></td>
    </tr><tr>
        <td class="line-number-row">   3</td>
        <td class="not-covered-line">class TwoSum {</td>
    </tr><tr>
        <td class="line-number-row">   4</td>
        <td class="uncovered-line"></td>
    </tr><tr>
        <td class="line-number-row">   5</td>
        <td class="uncovered-line">    companion object {</td>
    </tr><tr>
        <td class="line-number-row">   6</td>
        <td class="uncovered-line">        fun sumNumbers(a: Int, b: Int): Any {</td>
    </tr><tr>
        <td class="line-number-row">   7</td>
        <td class="covered-line">            return if (a == 0 && b == 0) {</td>
    </tr><tr>
        <td class="line-number-row">   8</td>
        <td class="covered-line">                "Both numbers are zero"</td>
    </tr><tr>
        <td class="line-number-row">   9</td>
        <td class="uncovered-line">            } else {</td>
    </tr><tr>
        <td class="line-number-row">  10</td>
        <td class="covered-line">                a + b</td>
    </tr><tr>
        <td class="line-number-row">  11</td>
        <td class="uncovered-line">            }</td>
    </tr><tr>
        <td class="line-number-row">  12</td>
        <td class="uncovered-line">        }</td>
    </tr><tr>
        <td class="line-number-row">  13</td>
        <td class="uncovered-line">    }</td>
    </tr><tr>
        <td class="line-number-row">  14</td>
        <td class="uncovered-line">}</td>
    </tr>    </tbody>
      </table>
    </body>
    </html>
      """.trimIndent()

    assertThat(outputReportText).isEqualTo(expectedResult)
  }

  @Test
  fun testRunCoverage_appTestsHTMLFormat_returnsCoverageData() {
    testBazelWorkspace.initEmptyWorkspace()

    val sourceContent =
      """
      package com.example
      
      class TwoSum {
      
          companion object {
              fun sumNumbers(a: Int, b: Int): Any {
                  return if (a == 0 && b == 0) {
                      "Both numbers are zero"
                  } else {
                      a + b
                  }
              }
          }
      }
      """.trimIndent()

    val testContent =
      """
      package com.example
      
      import org.junit.Assert.assertEquals
      import org.junit.Test
      
      class TwoSumTest {
      
          @Test
          fun testSumNumbers() {
              assertEquals(TwoSum.sumNumbers(0, 1), 1)
              assertEquals(TwoSum.sumNumbers(3, 4), 7)         
              assertEquals(TwoSum.sumNumbers(0, 0), "Both numbers are zero")
          }
      }
      """.trimIndent()

    testBazelWorkspace.addSourceAndTestFileWithContent(
      filename = "TwoSum",
      testFilename = "TwoSumTest",
      sourceContent = sourceContent,
      testContent = testContent,
      sourceSubpackage = "app/main/java/com/example",
      testSubpackage = "app/test/java/com/example"
    )

    RunCoverage(
      "${tempFolder.root}",
      "app/main/java/com/example/TwoSum.kt",
      ReportFormat.HTML,
      sampleHTMLOutputPath,
      longCommandExecutor,
      scriptBgDispatcher
    ).execute()

    val outputReportText = File(sampleHTMLOutputPath).readText()

    val expectedResult =
      """
    <!DOCTYPE html>
    <html lang="en">
    <head>
      <meta charset="UTF-8">
      <meta name="viewport" content="width=device-width, initial-scale=1.0">
      <title>Coverage Report</title>
      <style>
        body {
            font-family: Arial, sans-serif;
            line-height: 1.6;
            padding: 20px;
        }
        table {
            width: 100%;
            border-collapse: collapse;
            margin-bottom: 20px;
        }
        th, td {
            padding: 8px;
            margin-left: 20px;
            text-align: left;
            border-bottom: 1px solid #fdfdfd;
        }
        .line-number-col {
            width: 2%;
        }
        .line-number-row {
            border-right: 1px dashed #000000
        }
        .source-code-col {
            width: 98%;
        }
        .covered-line, .not-covered-line, .uncovered-line {
            white-space: pre-wrap;
            word-wrap: break-word;
            box-sizing: border-box;
            border-radius: 4px;
            padding: 2px 8px 2px 4px;
            display: inline-block;
        }
        .covered-line {
            background-color: #c8e6c9; /* Light green */
        }
        .not-covered-line {
            background-color: #ffcdd2; /* Light red */
        }
        .uncovered-line {
            background-color: #f1f1f1; /* light gray */
        }
        .coverage-summary {
          margin-bottom: 20px;
        }
        h2 {
          text-align: center;
        }
        ul {
          list-style-type: none;
          padding: 0;
          text-align: center;
        }
        .summary-box {
          background-color: #f0f0f0;
          border: 1px solid #ccc;
          border-radius: 8px;
          padding: 10px;
          margin-bottom: 20px;
          display: flex;
          justify-content: space-between;
          align-items: flex-start;
        }
        .summary-left {
          text-align: left;
        }
        .summary-right {
          text-align: right;
        }
        .legend {
          display: flex;
          align-items: center;
        }
        .legend-item {
          width: 20px;
          height: 10px;
          margin-right: 5px;
          border-radius: 2px;
          display: inline-block;
        }
        .legend .covered {
          background-color: #c8e6c9; /* Light green */
        }
        .legend .not-covered {
          margin-left: 4px;
          background-color: #ffcdd2; /* Light red */
        }
        @media screen and (max-width: 768px) {
            body {
                padding: 10px;
            }
            table {
                width: auto;
            }
        }
      </style>
    </head>
    <body>
      <h2>Coverage Report</h2>
      <div class="summary-box">
        <div class="summary-left">
          <strong>Covered File:</strong> app/main/java/com/example/TwoSum.kt <br>
          <div class="legend">
            <div class="legend-item covered"></div>
            <span>Covered</span>
            <div class="legend-item not-covered"></div>
            <span>Uncovered</span>
          </div>
        </div>
        <div class="summary-right">
          <div><strong>Coverage percentage:</strong> 75.00%</div>
          <div><strong>Line coverage:</strong> 3 / 4 covered</div>
        </div>
      </div>
      <table>
        <thead>
          <tr>
            <th class="line-number-col">Line No</th>
            <th class="source-code-col">Source Code</th>
          </tr>
        </thead>
        <tbody><tr>
        <td class="line-number-row">   1</td>
        <td class="uncovered-line">package com.example</td>
    </tr><tr>
        <td class="line-number-row">   2</td>
        <td class="uncovered-line"></td>
    </tr><tr>
        <td class="line-number-row">   3</td>
        <td class="not-covered-line">class TwoSum {</td>
    </tr><tr>
        <td class="line-number-row">   4</td>
        <td class="uncovered-line"></td>
    </tr><tr>
        <td class="line-number-row">   5</td>
        <td class="uncovered-line">    companion object {</td>
    </tr><tr>
        <td class="line-number-row">   6</td>
        <td class="uncovered-line">        fun sumNumbers(a: Int, b: Int): Any {</td>
    </tr><tr>
        <td class="line-number-row">   7</td>
        <td class="covered-line">            return if (a == 0 && b == 0) {</td>
    </tr><tr>
        <td class="line-number-row">   8</td>
        <td class="covered-line">                "Both numbers are zero"</td>
    </tr><tr>
        <td class="line-number-row">   9</td>
        <td class="uncovered-line">            } else {</td>
    </tr><tr>
        <td class="line-number-row">  10</td>
        <td class="covered-line">                a + b</td>
    </tr><tr>
        <td class="line-number-row">  11</td>
        <td class="uncovered-line">            }</td>
    </tr><tr>
        <td class="line-number-row">  12</td>
        <td class="uncovered-line">        }</td>
    </tr><tr>
        <td class="line-number-row">  13</td>
        <td class="uncovered-line">    }</td>
    </tr><tr>
        <td class="line-number-row">  14</td>
        <td class="uncovered-line">}</td>
    </tr>    </tbody>
      </table>
    </body>
    </html>
      """.trimIndent()

    assertThat(outputReportText).isEqualTo(expectedResult)
  }

  @Test
  fun testRunCoverage_localTestsHTMLFormat_returnsCoverageData() {
    testBazelWorkspace.initEmptyWorkspace()

    val sourceContent =
      """
      package com.example
      
      class TwoSum {
      
          companion object {
              fun sumNumbers(a: Int, b: Int): Any {
                  return if (a == 0 && b == 0) {
                      "Both numbers are zero"
                  } else {
                      a + b
                  }
              }
          }
      }
      """.trimIndent()

    val testContent =
      """
      package com.example
      
      import org.junit.Assert.assertEquals
      import org.junit.Test
      
      class TwoSumLocalTest {
      
          @Test
          fun testSumNumbers() {
              assertEquals(TwoSum.sumNumbers(0, 1), 1)
              assertEquals(TwoSum.sumNumbers(3, 4), 7)         
              assertEquals(TwoSum.sumNumbers(0, 0), "Both numbers are zero")
          }
      }
      """.trimIndent()

    testBazelWorkspace.addSourceAndTestFileWithContent(
      filename = "TwoSum",
      testFilename = "TwoSumLocalTest",
      sourceContent = sourceContent,
      testContent = testContent,
      sourceSubpackage = "app/main/java/com/example",
      testSubpackage = "app/test/java/com/example"
    )

    RunCoverage(
      "${tempFolder.root}",
      "app/main/java/com/example/TwoSum.kt",
      ReportFormat.HTML,
      sampleHTMLOutputPath,
      longCommandExecutor,
      scriptBgDispatcher
    ).execute()

    val outputReportText = File(sampleHTMLOutputPath).readText()

    val expectedResult =
      """
    <!DOCTYPE html>
    <html lang="en">
    <head>
      <meta charset="UTF-8">
      <meta name="viewport" content="width=device-width, initial-scale=1.0">
      <title>Coverage Report</title>
      <style>
        body {
            font-family: Arial, sans-serif;
            line-height: 1.6;
            padding: 20px;
        }
        table {
            width: 100%;
            border-collapse: collapse;
            margin-bottom: 20px;
        }
        th, td {
            padding: 8px;
            margin-left: 20px;
            text-align: left;
            border-bottom: 1px solid #fdfdfd;
        }
        .line-number-col {
            width: 2%;
        }
        .line-number-row {
            border-right: 1px dashed #000000
        }
        .source-code-col {
            width: 98%;
        }
        .covered-line, .not-covered-line, .uncovered-line {
            white-space: pre-wrap;
            word-wrap: break-word;
            box-sizing: border-box;
            border-radius: 4px;
            padding: 2px 8px 2px 4px;
            display: inline-block;
        }
        .covered-line {
            background-color: #c8e6c9; /* Light green */
        }
        .not-covered-line {
            background-color: #ffcdd2; /* Light red */
        }
        .uncovered-line {
            background-color: #f1f1f1; /* light gray */
        }
        .coverage-summary {
          margin-bottom: 20px;
        }
        h2 {
          text-align: center;
        }
        ul {
          list-style-type: none;
          padding: 0;
          text-align: center;
        }
        .summary-box {
          background-color: #f0f0f0;
          border: 1px solid #ccc;
          border-radius: 8px;
          padding: 10px;
          margin-bottom: 20px;
          display: flex;
          justify-content: space-between;
          align-items: flex-start;
        }
        .summary-left {
          text-align: left;
        }
        .summary-right {
          text-align: right;
        }
        .legend {
          display: flex;
          align-items: center;
        }
        .legend-item {
          width: 20px;
          height: 10px;
          margin-right: 5px;
          border-radius: 2px;
          display: inline-block;
        }
        .legend .covered {
          background-color: #c8e6c9; /* Light green */
        }
        .legend .not-covered {
          margin-left: 4px;
          background-color: #ffcdd2; /* Light red */
        }
        @media screen and (max-width: 768px) {
            body {
                padding: 10px;
            }
            table {
                width: auto;
            }
        }
      </style>
    </head>
    <body>
      <h2>Coverage Report</h2>
      <div class="summary-box">
        <div class="summary-left">
          <strong>Covered File:</strong> app/main/java/com/example/TwoSum.kt <br>
          <div class="legend">
            <div class="legend-item covered"></div>
            <span>Covered</span>
            <div class="legend-item not-covered"></div>
            <span>Uncovered</span>
          </div>
        </div>
        <div class="summary-right">
          <div><strong>Coverage percentage:</strong> 75.00%</div>
          <div><strong>Line coverage:</strong> 3 / 4 covered</div>
        </div>
      </div>
      <table>
        <thead>
          <tr>
            <th class="line-number-col">Line No</th>
            <th class="source-code-col">Source Code</th>
          </tr>
        </thead>
        <tbody><tr>
        <td class="line-number-row">   1</td>
        <td class="uncovered-line">package com.example</td>
    </tr><tr>
        <td class="line-number-row">   2</td>
        <td class="uncovered-line"></td>
    </tr><tr>
        <td class="line-number-row">   3</td>
        <td class="not-covered-line">class TwoSum {</td>
    </tr><tr>
        <td class="line-number-row">   4</td>
        <td class="uncovered-line"></td>
    </tr><tr>
        <td class="line-number-row">   5</td>
        <td class="uncovered-line">    companion object {</td>
    </tr><tr>
        <td class="line-number-row">   6</td>
        <td class="uncovered-line">        fun sumNumbers(a: Int, b: Int): Any {</td>
    </tr><tr>
        <td class="line-number-row">   7</td>
        <td class="covered-line">            return if (a == 0 && b == 0) {</td>
    </tr><tr>
        <td class="line-number-row">   8</td>
        <td class="covered-line">                "Both numbers are zero"</td>
    </tr><tr>
        <td class="line-number-row">   9</td>
        <td class="uncovered-line">            } else {</td>
    </tr><tr>
        <td class="line-number-row">  10</td>
        <td class="covered-line">                a + b</td>
    </tr><tr>
        <td class="line-number-row">  11</td>
        <td class="uncovered-line">            }</td>
    </tr><tr>
        <td class="line-number-row">  12</td>
        <td class="uncovered-line">        }</td>
    </tr><tr>
        <td class="line-number-row">  13</td>
        <td class="uncovered-line">    }</td>
    </tr><tr>
        <td class="line-number-row">  14</td>
        <td class="uncovered-line">}</td>
    </tr>    </tbody>
      </table>
    </body>
    </html>
      """.trimIndent()

    assertThat(outputReportText).isEqualTo(expectedResult)
  }

  @Test
  fun testRunCoverage_sharedTestsHTMLFormat_returnsCoverageData() {
    testBazelWorkspace.initEmptyWorkspace()

    val sourceContent =
      """
      package com.example
      
      class TwoSum {
      
          companion object {
              fun sumNumbers(a: Int, b: Int): Any {
                  return if (a == 0 && b == 0) {
                      "Both numbers are zero"
                  } else {
                      a + b
                  }
              }
          }
      }
      """.trimIndent()

    val testContent =
      """
      package com.example
      
      import org.junit.Assert.assertEquals
      import org.junit.Test
      
      class TwoSumTest {
      
          @Test
          fun testSumNumbers() {
              assertEquals(TwoSum.sumNumbers(0, 1), 1)
              assertEquals(TwoSum.sumNumbers(3, 4), 7)         
              assertEquals(TwoSum.sumNumbers(0, 0), "Both numbers are zero")
          }
      }
      """.trimIndent()

    testBazelWorkspace.addSourceAndTestFileWithContent(
      filename = "TwoSum",
      testFilename = "TwoSumTest",
      sourceContent = sourceContent,
      testContent = testContent,
      sourceSubpackage = "app/main/java/com/example",
      testSubpackage = "app/sharedTest/java/com/example"
    )

    RunCoverage(
      "${tempFolder.root}",
      "app/main/java/com/example/TwoSum.kt",
      ReportFormat.HTML,
      sampleHTMLOutputPath,
      longCommandExecutor,
      scriptBgDispatcher
    ).execute()

    val outputReportText = File(sampleHTMLOutputPath).readText()

    val expectedResult =
      """
    <!DOCTYPE html>
    <html lang="en">
    <head>
      <meta charset="UTF-8">
      <meta name="viewport" content="width=device-width, initial-scale=1.0">
      <title>Coverage Report</title>
      <style>
        body {
            font-family: Arial, sans-serif;
            line-height: 1.6;
            padding: 20px;
        }
        table {
            width: 100%;
            border-collapse: collapse;
            margin-bottom: 20px;
        }
        th, td {
            padding: 8px;
            margin-left: 20px;
            text-align: left;
            border-bottom: 1px solid #fdfdfd;
        }
        .line-number-col {
            width: 2%;
        }
        .line-number-row {
            border-right: 1px dashed #000000
        }
        .source-code-col {
            width: 98%;
        }
        .covered-line, .not-covered-line, .uncovered-line {
            white-space: pre-wrap;
            word-wrap: break-word;
            box-sizing: border-box;
            border-radius: 4px;
            padding: 2px 8px 2px 4px;
            display: inline-block;
        }
        .covered-line {
            background-color: #c8e6c9; /* Light green */
        }
        .not-covered-line {
            background-color: #ffcdd2; /* Light red */
        }
        .uncovered-line {
            background-color: #f1f1f1; /* light gray */
        }
        .coverage-summary {
          margin-bottom: 20px;
        }
        h2 {
          text-align: center;
        }
        ul {
          list-style-type: none;
          padding: 0;
          text-align: center;
        }
        .summary-box {
          background-color: #f0f0f0;
          border: 1px solid #ccc;
          border-radius: 8px;
          padding: 10px;
          margin-bottom: 20px;
          display: flex;
          justify-content: space-between;
          align-items: flex-start;
        }
        .summary-left {
          text-align: left;
        }
        .summary-right {
          text-align: right;
        }
        .legend {
          display: flex;
          align-items: center;
        }
        .legend-item {
          width: 20px;
          height: 10px;
          margin-right: 5px;
          border-radius: 2px;
          display: inline-block;
        }
        .legend .covered {
          background-color: #c8e6c9; /* Light green */
        }
        .legend .not-covered {
          margin-left: 4px;
          background-color: #ffcdd2; /* Light red */
        }
        @media screen and (max-width: 768px) {
            body {
                padding: 10px;
            }
            table {
                width: auto;
            }
        }
      </style>
    </head>
    <body>
      <h2>Coverage Report</h2>
      <div class="summary-box">
        <div class="summary-left">
          <strong>Covered File:</strong> app/main/java/com/example/TwoSum.kt <br>
          <div class="legend">
            <div class="legend-item covered"></div>
            <span>Covered</span>
            <div class="legend-item not-covered"></div>
            <span>Uncovered</span>
          </div>
        </div>
        <div class="summary-right">
          <div><strong>Coverage percentage:</strong> 75.00%</div>
          <div><strong>Line coverage:</strong> 3 / 4 covered</div>
        </div>
      </div>
      <table>
        <thead>
          <tr>
            <th class="line-number-col">Line No</th>
            <th class="source-code-col">Source Code</th>
          </tr>
        </thead>
        <tbody><tr>
        <td class="line-number-row">   1</td>
        <td class="uncovered-line">package com.example</td>
    </tr><tr>
        <td class="line-number-row">   2</td>
        <td class="uncovered-line"></td>
    </tr><tr>
        <td class="line-number-row">   3</td>
        <td class="not-covered-line">class TwoSum {</td>
    </tr><tr>
        <td class="line-number-row">   4</td>
        <td class="uncovered-line"></td>
    </tr><tr>
        <td class="line-number-row">   5</td>
        <td class="uncovered-line">    companion object {</td>
    </tr><tr>
        <td class="line-number-row">   6</td>
        <td class="uncovered-line">        fun sumNumbers(a: Int, b: Int): Any {</td>
    </tr><tr>
        <td class="line-number-row">   7</td>
        <td class="covered-line">            return if (a == 0 && b == 0) {</td>
    </tr><tr>
        <td class="line-number-row">   8</td>
        <td class="covered-line">                "Both numbers are zero"</td>
    </tr><tr>
        <td class="line-number-row">   9</td>
        <td class="uncovered-line">            } else {</td>
    </tr><tr>
        <td class="line-number-row">  10</td>
        <td class="covered-line">                a + b</td>
    </tr><tr>
        <td class="line-number-row">  11</td>
        <td class="uncovered-line">            }</td>
    </tr><tr>
        <td class="line-number-row">  12</td>
        <td class="uncovered-line">        }</td>
    </tr><tr>
        <td class="line-number-row">  13</td>
        <td class="uncovered-line">    }</td>
    </tr><tr>
        <td class="line-number-row">  14</td>
        <td class="uncovered-line">}</td>
    </tr>    </tbody>
      </table>
    </body>
    </html>
      """.trimIndent()

    assertThat(outputReportText).isEqualTo(expectedResult)
  }

  @Test
  fun testRunCoverage_sharedAndLocalTestsHTMLFormat_returnsCoverageData() {
    testBazelWorkspace.initEmptyWorkspace()

    val sourceContent =
      """
      package com.example
      
      class TwoSum {
      
          companion object {
              fun sumNumbers(a: Int, b: Int): Any {
                  return if (a == 0 && b == 0) {
                      "Both numbers are zero"
                  } else {
                      a + b
                  }
              }
          }
      }
      """.trimIndent()

    val testContentShared =
      """
      package com.example
      
      import org.junit.Assert.assertEquals
      import org.junit.Test
      
      class TwoSumTest {
      
          @Test
          fun testSumNumbers() {
              assertEquals(TwoSum.sumNumbers(0, 1), 1)
              assertEquals(TwoSum.sumNumbers(3, 4), 7)         
              assertEquals(TwoSum.sumNumbers(0, 0), "Both numbers are zero")
          }
      }
      """.trimIndent()

    val testContentLocal =
      """
      package com.example
      
      import org.junit.Assert.assertEquals
      import org.junit.Test
      
      class TwoSumLocalTest {
      
          @Test
          fun testSumNumbers() {
              assertEquals(TwoSum.sumNumbers(0, 1), 1)
              assertEquals(TwoSum.sumNumbers(3, 4), 7)         
              assertEquals(TwoSum.sumNumbers(0, 0), "Both numbers are zero")
          }
      }
      """.trimIndent()

    testBazelWorkspace.addMultiLevelSourceAndTestFileWithContent(
      filename = "TwoSum",
      sourceContent = sourceContent,
      testContentShared = testContentShared,
      testContentLocal = testContentLocal,
      subpackage = "app"
    )

    RunCoverage(
      "${tempFolder.root}",
      "app/main/java/com/example/TwoSum.kt",
      ReportFormat.HTML,
      sampleHTMLOutputPath,
      longCommandExecutor,
      scriptBgDispatcher
    ).execute()

    val outputReportText = File(sampleHTMLOutputPath).readText()

    val expectedResult =
      """
    <!DOCTYPE html>
    <html lang="en">
    <head>
      <meta charset="UTF-8">
      <meta name="viewport" content="width=device-width, initial-scale=1.0">
      <title>Coverage Report</title>
      <style>
        body {
            font-family: Arial, sans-serif;
            line-height: 1.6;
            padding: 20px;
        }
        table {
            width: 100%;
            border-collapse: collapse;
            margin-bottom: 20px;
        }
        th, td {
            padding: 8px;
            margin-left: 20px;
            text-align: left;
            border-bottom: 1px solid #fdfdfd;
        }
        .line-number-col {
            width: 2%;
        }
        .line-number-row {
            border-right: 1px dashed #000000
        }
        .source-code-col {
            width: 98%;
        }
        .covered-line, .not-covered-line, .uncovered-line {
            white-space: pre-wrap;
            word-wrap: break-word;
            box-sizing: border-box;
            border-radius: 4px;
            padding: 2px 8px 2px 4px;
            display: inline-block;
        }
        .covered-line {
            background-color: #c8e6c9; /* Light green */
        }
        .not-covered-line {
            background-color: #ffcdd2; /* Light red */
        }
        .uncovered-line {
            background-color: #f1f1f1; /* light gray */
        }
        .coverage-summary {
          margin-bottom: 20px;
        }
        h2 {
          text-align: center;
        }
        ul {
          list-style-type: none;
          padding: 0;
          text-align: center;
        }
        .summary-box {
          background-color: #f0f0f0;
          border: 1px solid #ccc;
          border-radius: 8px;
          padding: 10px;
          margin-bottom: 20px;
          display: flex;
          justify-content: space-between;
          align-items: flex-start;
        }
        .summary-left {
          text-align: left;
        }
        .summary-right {
          text-align: right;
        }
        .legend {
          display: flex;
          align-items: center;
        }
        .legend-item {
          width: 20px;
          height: 10px;
          margin-right: 5px;
          border-radius: 2px;
          display: inline-block;
        }
        .legend .covered {
          background-color: #c8e6c9; /* Light green */
        }
        .legend .not-covered {
          margin-left: 4px;
          background-color: #ffcdd2; /* Light red */
        }
        @media screen and (max-width: 768px) {
            body {
                padding: 10px;
            }
            table {
                width: auto;
            }
        }
      </style>
    </head>
    <body>
      <h2>Coverage Report</h2>
      <div class="summary-box">
        <div class="summary-left">
          <strong>Covered File:</strong> app/main/java/com/example/TwoSum.kt <br>
          <div class="legend">
            <div class="legend-item covered"></div>
            <span>Covered</span>
            <div class="legend-item not-covered"></div>
            <span>Uncovered</span>
          </div>
        </div>
        <div class="summary-right">
          <div><strong>Coverage percentage:</strong> 75.00%</div>
          <div><strong>Line coverage:</strong> 3 / 4 covered</div>
        </div>
      </div>
      <table>
        <thead>
          <tr>
            <th class="line-number-col">Line No</th>
            <th class="source-code-col">Source Code</th>
          </tr>
        </thead>
        <tbody><tr>
        <td class="line-number-row">   1</td>
        <td class="uncovered-line">package com.example</td>
    </tr><tr>
        <td class="line-number-row">   2</td>
        <td class="uncovered-line"></td>
    </tr><tr>
        <td class="line-number-row">   3</td>
        <td class="not-covered-line">class TwoSum {</td>
    </tr><tr>
        <td class="line-number-row">   4</td>
        <td class="uncovered-line"></td>
    </tr><tr>
        <td class="line-number-row">   5</td>
        <td class="uncovered-line">    companion object {</td>
    </tr><tr>
        <td class="line-number-row">   6</td>
        <td class="uncovered-line">        fun sumNumbers(a: Int, b: Int): Any {</td>
    </tr><tr>
        <td class="line-number-row">   7</td>
        <td class="covered-line">            return if (a == 0 && b == 0) {</td>
    </tr><tr>
        <td class="line-number-row">   8</td>
        <td class="covered-line">                "Both numbers are zero"</td>
    </tr><tr>
        <td class="line-number-row">   9</td>
        <td class="uncovered-line">            } else {</td>
    </tr><tr>
        <td class="line-number-row">  10</td>
        <td class="covered-line">                a + b</td>
    </tr><tr>
        <td class="line-number-row">  11</td>
        <td class="uncovered-line">            }</td>
    </tr><tr>
        <td class="line-number-row">  12</td>
        <td class="uncovered-line">        }</td>
    </tr><tr>
        <td class="line-number-row">  13</td>
        <td class="uncovered-line">    }</td>
    </tr><tr>
        <td class="line-number-row">  14</td>
        <td class="uncovered-line">}</td>
    </tr>    </tbody>
      </table>
    </body>
    </html>
      """.trimIndent()

    assertThat(outputReportText).isEqualTo(expectedResult)
  }

  private fun initializeCommandExecutorWithLongProcessWaitTime(): CommandExecutorImpl {
    return CommandExecutorImpl(
      scriptBgDispatcher, processTimeout = 5, processTimeoutUnit = TimeUnit.MINUTES
    )
  }
}
