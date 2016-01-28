/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.tests.gui.gradle;

import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.gradle.project.compatibility.VersionCompatibilityService;
import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture.ContentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture.MessageFixture;
import org.intellij.lang.annotations.Language;
import org.jdom.JDOMException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static com.android.tools.idea.tests.gui.framework.TestGroup.PROJECT_SUPPORT;
import static com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture.MessageMatcher.firstLineStartingWith;
import static com.intellij.ide.errorTreeView.ErrorTreeElementKind.ERROR;
import static com.intellij.ide.errorTreeView.ErrorTreeElementKind.WARNING;
import static org.fest.assertions.Assertions.assertThat;

@BelongsToTestGroups({PROJECT_SUPPORT})
public class ComponentVersionCheckTest extends GuiTestCase {

  @Before
  public void skipSourceGenerationOnSync() {
    GradleExperimentalSettings.getInstance().SKIP_SOURCE_GEN_ON_PROJECT_SYNC = true;
  }

  @After
  public void removeTestMetadata() {
    VersionCompatibilityService.getInstance().reloadMetadata();
  }

  @Test
  public void testCompatibilityCheckBetweenGradleAndAndroidGradlePlugin() throws IOException, JDOMException {
    @Language("XML")
    String metadata = "<compatibility version='1'>\n" +
                      "  <check failureType='error'>\n" +
                      "    <component name='gradle' version='[2.4, +)'>\n" +
                      "      <requires name='android-gradle-plugin' version='[1.5.0, +]'>\n" +
                      "        <failureMsg>\n" +
                      "           <![CDATA[\n" +
                      "Please use Android Gradle plugin 1.5.0 or newer.\n" +
                      "]]>\n" +
                      "        </failureMsg>\n" +
                      "      </requires>\n" +
                      "    </component>\n" +
                      "  </check>\n" +
                      "</compatibility>";
    VersionCompatibilityService.getInstance().reloadMetadataForTesting(metadata);
    importSimpleApplication();

    getIdeFrame().updateGradleWrapperVersion("2.4").updateAndroidGradlePluginVersion("1.3.0").requestProjectSync()
      .waitForGradleProjectSyncToFinish();

    ContentFixture syncMessages = getIdeFrame().getMessagesToolWindow().getGradleSyncContent();
    MessageFixture message = syncMessages.findMessage(ERROR, firstLineStartingWith("Gradle 2.4 requires Android Gradle plugin 1.5.0"));

    String text = message.getText();
    assertThat(text).as("custom failure message").contains("Please use Android Gradle plugin 1.5.0 or newer.");

    message.findHyperlink("Fix plugin version and sync project");
  }

  @Test
  public void testCompatibilityCheckBetweenBuildToolsAndAndroidGradlePlugin() throws IOException, JDOMException {
    @Language("XML")
    String metadata = "<compatiblity version='1'>\n" +
                      "  <check failureType='warning'>\n" +
                      "    <component name='buildFile:android/buildToolsVersion' version='[19, +)'>\n" +
                      "      <requires name='android-gradle-plugin' version='[1.5.0, +)'>\n" +
                      "        <failureMsg>\n" +
                      "        <![CDATA[\n" +
                      "The project will not build.\\n\n" +
                      "Please use Android Gradle plugin 1.5.0 or newer.\n" +
                      "]]>\n" +
                      "        </failureMsg>\n" +
                      "      </requires>\n" +
                      "    </component>\n" +
                      "  </check>\n" +
                      "</compatiblity>\n";
    VersionCompatibilityService.getInstance().reloadMetadataForTesting(metadata);
    importSimpleApplication();

    getIdeFrame().updateGradleWrapperVersion("2.4").updateAndroidGradlePluginVersion("1.3.0").requestProjectSync()
      .waitForGradleProjectSyncToFinish();

    ContentFixture syncMessages = getIdeFrame().getMessagesToolWindow().getGradleSyncContent();
    MessageFixture message =
      syncMessages.findMessage(WARNING, firstLineStartingWith("'buildToolsVersion' 19.1.0 requires Android Gradle plugin 1.5.0"));

    String text = message.getText();
    assertThat(text).as("custom failure message").contains("The project will not build.")
      .contains("Please use Android Gradle plugin 1.5.0 or newer.");
  }
}
