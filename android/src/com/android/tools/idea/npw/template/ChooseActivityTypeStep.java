/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.npw.template;


import com.android.tools.idea.npw.ActivityGalleryStep;
import com.android.tools.idea.npw.ThemeHelper;
import com.android.tools.idea.npw.module.NewModuleModel;
import com.android.tools.idea.npw.platform.AndroidVersionsInfo;
import com.android.tools.idea.npw.project.AndroidSourceSet;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.ui.ASGallery;
import com.android.tools.idea.ui.properties.core.ObservableBool;
import com.android.tools.idea.ui.properties.core.StringProperty;
import com.android.tools.idea.ui.properties.core.StringValueProperty;
import com.android.tools.idea.ui.validation.Validator;
import com.android.tools.idea.ui.validation.ValidatorPanel;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import com.android.tools.swing.util.FormScalingUtil;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

import static com.android.tools.idea.wizard.WizardConstants.DEFAULT_GALLERY_THUMBNAIL_SIZE;
import static org.jetbrains.android.util.AndroidBundle.message;

/**
 * This step allows the user to select which type of Activity they want to create.
 * TODO: This step can be used as part of the "New Project" flow. In that flow, if the "Has CPP support" is selected, we should not show
 * this step, but the next step should be "Basic Activity". In the current work flow (using the dynamic wizard), this was difficult to do,
 * so instead {@link ActivityGalleryStep} was always shown with three options ("Add no Activity", "Basic Activity" and "Empty Activity").
 * The code to filter out the activities is {@link TemplateListProvider}
 * TODO: ATTR_IS_LAUNCHER seems to be dead code, it was one option in the old UI flow. Find out if we can remove it.
 * TODO: Extending RenderTemplateModel don't seem to match with the things ChooseActivityTypeStep needs to be configured... For example,
 * it needs to know "Is Cpp Project" (to adjust the list of templates or hide itself).
 * TODO: This class and ChooseModuleTypeStep looks to have a lot in common. Should we have something more specific than a ASGallery,
 * that renders "Gallery items"?
 */
public class ChooseActivityTypeStep extends SkippableWizardStep<NewModuleModel> {
  private final RenderTemplateModel myRenderModel;
  private @NotNull TemplateRenderer[] myTemplateList;
  private @NotNull List<AndroidSourceSet> mySourceSets;

  private @NotNull ASGallery<TemplateRenderer> myActivityGallery;
  private @NotNull ValidatorPanel myValidatorPanel;
  private final StringProperty myInvalidParameterMessage = new StringValueProperty();

  private @Nullable AndroidFacet myFacet;
  private boolean myAppThemeExists;

  public ChooseActivityTypeStep(@NotNull NewModuleModel moduleModel,
                                @NotNull RenderTemplateModel renderModel,
                                @NotNull List<TemplateHandle> templateList,
                                @NotNull List<AndroidSourceSet> sourceSets) {
    this(moduleModel, renderModel);
    init(templateList, sourceSets, null);
  }

  public ChooseActivityTypeStep(@NotNull NewModuleModel moduleModel,
                                @NotNull RenderTemplateModel renderModel,
                                @NotNull AndroidFacet facet,
                                @NotNull List<TemplateHandle> templateList,
                                @NotNull VirtualFile targetDirectory) {
    this(moduleModel, renderModel);
    List<AndroidSourceSet> sourceSets = AndroidSourceSet.getSourceSets(facet, targetDirectory);
    init(templateList, sourceSets, facet);
  }

  private ChooseActivityTypeStep(@NotNull NewModuleModel moduleModel, @NotNull RenderTemplateModel renderModel) {
    super(moduleModel, message("android.wizard.activity.add", renderModel.getTemplateHandle().getMetadata().getFormFactor()));
    this.myRenderModel = renderModel;
  }

  private void init(@NotNull List<TemplateHandle> templateList,
                    @NotNull List<AndroidSourceSet> sourceSets,
                    @Nullable AndroidFacet facet) {
    mySourceSets = sourceSets;
    myFacet = facet;
    myAppThemeExists = isNewModule() || new ThemeHelper(facet.getModule()).getAppThemeName() != null;

    // For any new module, we need a "Add No Activity" entry.
    int i = isNewModule() ? 1 : 0;
    myTemplateList = new TemplateRenderer[templateList.size() + i];
    if (isNewModule()) {
      myTemplateList[0] = new TemplateRenderer(null);
    }
    for (TemplateHandle templateHandle : templateList) {
      myTemplateList[i] = new TemplateRenderer(templateHandle);
      i++;
    }

    myActivityGallery = createGallery(getTitle());
    myValidatorPanel = new ValidatorPanel(this, new JBScrollPane(myActivityGallery));
    FormScalingUtil.scaleComponentTree(this.getClass(), myValidatorPanel);
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myValidatorPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myActivityGallery;
  }

  @NotNull
  @Override
  public Collection<? extends ModelWizardStep> createDependentSteps() {
    String title = message("android.wizard.config.activity.title");
    return Lists.newArrayList(new ConfigureTemplateParametersStep(myRenderModel, title, mySourceSets, myFacet));
  }

  private static ASGallery<TemplateRenderer> createGallery(String title) {
    ASGallery<TemplateRenderer> gallery = new ASGallery<TemplateRenderer>(
      JBList.createDefaultListModel(),
      TemplateRenderer::getImage,
      TemplateRenderer::getTitle,
      DEFAULT_GALLERY_THUMBNAIL_SIZE,
      null
    ) {

      @Override
      public Dimension getPreferredScrollableViewportSize() {
        Dimension cellSize = computeCellSize();
        int heightInsets = getInsets().top + getInsets().bottom;
        int widthInsets = getInsets().left + getInsets().right;
        // Don't want to show an exact number of rows, since then it's not obvious there's another row available.
        return new Dimension(cellSize.width * 5 + widthInsets, (int)(cellSize.height * 2.2) + heightInsets);
      }
    };

    gallery.setBorder(BorderFactory.createLineBorder(JBColor.border()));
    AccessibleContextUtil.setDescription(gallery, title);

    return gallery;
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    myValidatorPanel.registerMessageSource(myInvalidParameterMessage);

    myActivityGallery.setModel(JBList.createDefaultListModel((Object[])myTemplateList));
    myActivityGallery.setDefaultAction(new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        wizard.goForward();
      }
    });

    myActivityGallery.addListSelectionListener(listSelectionEvent -> {
      TemplateRenderer selectedTemplate = myActivityGallery.getSelectedElement();
      if (selectedTemplate != null) {
        myRenderModel.setTemplateHandle(selectedTemplate.getTemplate());
        wizard.updateNavigationProperties();
      }
      validateTemplate();
    });

    int defaultSelection = getDefaultSelectedTemplateIndex(myTemplateList);
    myActivityGallery.setSelectedIndex(defaultSelection); // Also fires the Selection Listener
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  @Override
  protected void onEntering() {
    validateTemplate();
  }

  @Override
  protected void onProceeding() {
    // TODO: From David: Can we look into moving this logic into handleFinished?
    // There should be multiple hashtables that a model points to, which gets merged at the last second. That way, we can clear one of the
    // hashtables.

    NewModuleModel moduleModel = getModel();
    if (myRenderModel.getTemplateHandle() == null) { // "Add No Activity" selected
      moduleModel.setDefaultRenderTemplateValues(myRenderModel);
    }
    else {
      moduleModel.getRenderTemplateValues().setValue(myRenderModel.getTemplateValues());
    }

    new TemplateValueInjector(moduleModel.getTemplateValues())
      .setProjectDefaults(moduleModel.getProject().getValueOrNull(), moduleModel.applicationName().get());
  }

  private static int getDefaultSelectedTemplateIndex(@NotNull TemplateRenderer[] templateList) {
    for (int i = 0; i < templateList.length; i++) {
      if (templateList[i].getTitle().equals("Empty Activity")) {
        return i;
      }
    }
    return 0;
  }

  private boolean isNewModule() {
    return myFacet == null;
  }

  private void validateTemplate() {
    TemplateHandle template = myRenderModel.getTemplateHandle();
    TemplateMetadata templateData = (template == null) ? null : template.getMetadata();
    AndroidVersionsInfo.VersionItem androidSdkInfo = myRenderModel.androidSdkInfo().getValueOrNull();

    myInvalidParameterMessage.set(validateTemplate(templateData, androidSdkInfo, myAppThemeExists, isNewModule()));
  }

  private static String validateTemplate(@Nullable TemplateMetadata template,
                                         @Nullable AndroidVersionsInfo.VersionItem androidSdkInfo,
                                         boolean appThemeExists, boolean isNewModule) {
    if (template == null) {
      return isNewModule ? "" : message("android.wizard.activity.not.found");
    }

    if (androidSdkInfo != null) {
      if (androidSdkInfo.getApiLevel() < template.getMinSdk()) {
        return message("android.wizard.activity.invalid.min.sdk", template.getMinSdk());
      }

      if (androidSdkInfo.getBuildApiLevel() < template.getMinBuildApi()) {
        return message("android.wizard.activity.invalid.min.build", template.getMinBuildApi());
      }

      if (!appThemeExists && template.isAppThemeRequired()) {
        return message("android.wizard.activity.invalid.app.theme");
      }
    }

    return "";
  }

  private static class TemplateRenderer {
    @Nullable private final TemplateHandle myTemplate;

    TemplateRenderer(@Nullable TemplateHandle template) {
      this.myTemplate = template;
    }

    @Nullable
    TemplateHandle getTemplate() {
      return myTemplate;
    }

    @NotNull
    String getTitle() {
      return myTemplate == null ? message("android.wizard.gallery.item.add.no.activity") : myTemplate.getMetadata().getTitle();
    }

    /**
     * Return the image associated with the current template, if it specifies one, or null otherwise.
     */
    @Nullable
    Image getImage() {
      String thumb = myTemplate == null ? null : myTemplate.getMetadata().getThumbnailPath();
      if (thumb != null && !thumb.isEmpty()) {
        try {
          File file = new File(myTemplate.getRootPath(), thumb.replace('/', File.separatorChar));
          return file.isFile() ? ImageIO.read(file) : null;
        }
        catch (IOException e) {
          Logger.getInstance(ActivityGalleryStep.class).warn(e);
        }
      }
      return null;
    }
  }
}
