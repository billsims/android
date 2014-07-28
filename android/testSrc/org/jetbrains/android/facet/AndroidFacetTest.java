/*
 * Copyright (C) 2013 The Android Open Source Project
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
package org.jetbrains.android.facet;

import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import org.jetbrains.android.AndroidTestCase;

import static org.easymock.classextension.EasyMock.*;

/**
 * Tests for {@link AndroidFacet}.
 */
public class AndroidFacetTest extends AndroidTestCase {
  private IdeaAndroidProject myAndroidProject;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myAndroidProject = createMock(IdeaAndroidProject.class);
  }

  public void testProjectSyncCompletedNotification() {
    GradleSyncListener listener1 = createMock(GradleSyncListener.class);
    listener1.syncSucceeded(getProject());
    expectLastCall().once();

    replay(listener1);

    myFacet.addListener(listener1);
    // This should notify listener1.
    myFacet.setIdeaAndroidProject(myAndroidProject);
    notifyBuildComplete();
    verify(listener1);
  }

  private void notifyBuildComplete() {
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            getProject().getMessageBus().syncPublisher(GradleSyncState.GRADLE_SYNC_TOPIC).syncSucceeded(getProject());
          }
        });
      }
    }, ModalityState.NON_MODAL);
  }
}
