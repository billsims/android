/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.sketchImporter.ui;

import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchArtboard;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchExportFormat;
import org.jetbrains.annotations.NotNull;

/**
 * Holds the options the user chooses for each individual icon that is imported,
 * i.e. whether they want to hide the background or other things that may come up in the future.
 * <p>
 * With respect to the MVP pattern developed for the Sketch Importer UI, this class is part of the model that forms the backbone of the
 * information presented in the interface.
 */
public class AssetOptions {
  private String myName;
  private boolean myExportable;

  public AssetOptions(@NotNull String name, boolean isExportable) {
    myName = name;
    myExportable = isExportable;
  }

  public AssetOptions(@NotNull SketchArtboard artboard) {
    myName = getDefaultName(artboard);
    myExportable = artboard.getExportOptions().getExportFormats().length != 0;
  }

  /**
   * By default, an item is exportable if it has <b>at least one exportFormat</b> (regardless of
   * the specifics of the format -> users can mark an item as exportable in Sketch with one click).
   */
  public boolean isExportable() {
    return myExportable;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  public void setName(@NotNull String name) {
    myName = name;
  }

  @NotNull
  private static String getDefaultName(@NotNull SketchArtboard artboard) {
    String name = artboard.getName();

    if (artboard.getExportOptions().getExportFormats().length != 0) {
      SketchExportFormat format = artboard.getExportOptions().getExportFormats()[0];

      if (format.getNamingScheme() == SketchExportFormat.NAMING_SCHEME_PREFIX) {
        return format.getName() + name;
      }
      else if (format.getNamingScheme() == SketchExportFormat.NAMING_SCHEME_SUFFIX) {
        return name + format.getName();
      }
    }

    return name;
  }
}