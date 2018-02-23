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
package com.android.tools.idea.res;

import com.android.ide.common.rendering.api.*;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ValueXmlHelper;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.idea.AndroidPsiUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;

import static com.android.SdkConstants.*;

public class PsiResourceItem implements ResourceItem {
  private final XmlTag myTag;
  private final PsiFile myFile;
  @NotNull private final String myName;
  @NotNull private final ResourceType myType;
  @NotNull private final ResourceNamespace myNamespace;
  @Nullable private ResourceValue myResourceValue;
  @Nullable private PsiResourceFile mySource;

  /**
   * Creates a new {@link PsiResourceItem}.
   *
   * @param tag particular XML tag from which this item was created, or null if the item represents a whole file.
   *
   * @see ResourceHelper#isFileBasedResourceType(ResourceType)
   * @see ResourceItem#isFileBased()
   */
  PsiResourceItem(@NotNull String name, @NotNull ResourceType type, @NotNull ResourceNamespace namespace, @Nullable XmlTag tag,
                  @NotNull PsiFile file) {
    myName = name;
    myType = type;
    myNamespace = namespace;
    myTag = tag;
    myFile = file;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public ResourceType getType() {
    return myType;
  }

  @Nullable
  @Override
  public String getLibraryName() {
    return null;
  }

  @NotNull
  @Override
  public ResourceNamespace getNamespace() {
    return myNamespace;
  }

  @NotNull
  @Override
  public ResourceReference getReferenceToSelf() {
    return new ResourceReference(myNamespace, myType, myName);
  }

  @NotNull
  @Override
  public FolderConfiguration getConfiguration() {
    PsiResourceFile source = getSource();
    assert source != null : "getConfiguration called on a PsiResourceItem with no source";
    return source.getFolderConfiguration();
  }

  @NotNull
  @Override
  public String getKey() {
    String qualifiers = getConfiguration().getQualifierString();
    if (!qualifiers.isEmpty()) {
      return getType() + "-" + qualifiers + "/" + getName();
    }

    return getType() + "/" + getName();
  }

  @Nullable
  public PsiResourceFile getSource() {
    if (mySource != null) {
      return mySource;
    }

    PsiFile file = getPsiFile();
    if (file == null) {
      return null;
    }

    PsiElement parent = AndroidPsiUtils.getPsiParentSafely(file);

    if (!(parent instanceof PsiDirectory)) {
      return null;
    }

    String name = ((PsiDirectory)parent).getName();
    ResourceFolderType folderType = ResourceFolderType.getFolderType(name);
    if (folderType == null) {
      return null;
    }

    FolderConfiguration configuration = FolderConfiguration.getConfigForFolder(name);
    if (configuration == null) {
      return null;
    }

    PsiFile psiFile = getPsiFile();
    if (psiFile == null) {
      return null;
    }

    // PsiResourceFile constructor sets the source of this item.
    return new PsiResourceFile(psiFile, Collections.singletonList(this), folderType, configuration);
  }

  public void setSource(@Nullable PsiResourceFile source) {
    mySource = source;
  }

  /**
   * GETTER WITH SIDE EFFECTS that registers we have taken an interest in this value
   * so that if the value changes we will get a resource changed event fire.
   */
  @Nullable
  @Override
  public ResourceValue getResourceValue() {
    if (myResourceValue == null) {
      //noinspection VariableNotUsedInsideIf
      if (myTag == null) {
        PsiResourceFile source = getSource();
        assert source != null : "getResourceValue called on a PsiResourceItem with no source";
        // Density based resource value?
        ResourceType type = getType();
        Density density = type == ResourceType.DRAWABLE || type == ResourceType.MIPMAP ? getFolderDensity() : null;

        String path = null;
        VirtualFile virtualFile = source.getVirtualFile();
        if (virtualFile != null) {
          path = VfsUtilCore.virtualToIoFile(virtualFile).getAbsolutePath();
        }
        if (density != null) {
          myResourceValue = new DensityBasedResourceValue(getReferenceToSelf(),
                                                          path,
                                                          density,
                                                          null);
        } else {
          myResourceValue = new ResourceValue(getReferenceToSelf(),
                                              path,
                                              null);
        }
      } else {
        myResourceValue = parseXmlToResourceValue();
      }
    }

    return myResourceValue;
  }

  @Nullable
  @Override
  public File getFile() {
    PsiFile psiFile = getPsiFile();
    if (psiFile == null) {
      return null;
    }

    VirtualFile virtualFile = psiFile.getVirtualFile();
    return virtualFile == null ? null : VfsUtilCore.virtualToIoFile(virtualFile);
  }

  @Override
  public boolean isFileBased() {
    return myTag == null;
  }

  @Nullable
  private Density getFolderDensity() {
    FolderConfiguration configuration = getConfiguration();
    DensityQualifier densityQualifier = configuration.getDensityQualifier();
    if (densityQualifier != null) {
      return densityQualifier.getValue();
    }
    return null;
  }

  @Nullable
  private ResourceValue parseXmlToResourceValue() {
    assert myTag != null;
    XmlTag tag = getTag();

    if (tag == null || !tag.isValid()) {
      return null;
    }

    ResourceValue value;
    switch (getType()) {
      case STYLE:
        String parent = getAttributeValue(tag, ATTR_PARENT);
        value = parseStyleValue(tag, new StyleResourceValue(getReferenceToSelf(), parent, null));
        break;
      case DECLARE_STYLEABLE:
        value = parseDeclareStyleable(tag, new DeclareStyleableResourceValue(getReferenceToSelf(), null, null));
        break;
      case ATTR:
        value = parseAttrValue(tag, new AttrResourceValue(getReferenceToSelf(), null));
        break;
      case ARRAY:
        value = parseArrayValue(tag, new ArrayResourceValue(getReferenceToSelf(), null) {
          // Allow the user to specify a specific element to use via tools:index
          @Override
          protected int getDefaultIndex() {
            String index = tag.getAttributeValue(ATTR_INDEX, TOOLS_URI);
            if (index != null) {
              return Integer.parseInt(index);
            }
            return super.getDefaultIndex();
          }
        });
        break;
      case PLURALS:
        value = parsePluralsValue(tag, new PluralsResourceValue(getReferenceToSelf(), null, null) {
          // Allow the user to specify a specific quantity to use via tools:quantity
          @Override
          public String getValue() {
            String quantity = tag.getAttributeValue(ATTR_QUANTITY, TOOLS_URI);
            if (quantity != null) {
              String value = getValue(quantity);
              if (value != null) {
                return value;
              }
            }
            return super.getValue();
          }
        });
        break;
      case STRING:
        value = parseTextValue(tag, new PsiTextResourceValue(getReferenceToSelf(), null, null, null));
        break;
      default:
        value = parseValue(tag, new ResourceValue(getReferenceToSelf(), null));
        break;
    }

    value.setNamespaceResolver(ResourceHelper.getNamespaceResolver(tag));
    return value;
  }

  @Nullable
  private static String getAttributeValue(@NotNull XmlTag tag, @NotNull String attributeName) {
    return tag.getAttributeValue(attributeName);
  }

  @NotNull
  private ResourceValue parseDeclareStyleable(@NotNull XmlTag tag, @NotNull DeclareStyleableResourceValue declareStyleable) {
    for (XmlTag child : tag.getSubTags()) {
      String name = getAttributeValue(child, ATTR_NAME);
      if (!StringUtil.isEmpty(name)) {
        ResourceUrl url = ResourceUrl.parseAttrReference(name);
        if (url != null) {
          ResourceReference resolvedAttr = url.resolve(getNamespace(), ResourceHelper.getNamespaceResolver(tag));
          if (resolvedAttr != null) {
            AttrResourceValue attr = parseAttrValue(child, new AttrResourceValue(resolvedAttr, null));
            declareStyleable.addValue(attr);
          }
        }

      }
    }

    return declareStyleable;
  }

  @NotNull
  private static ResourceValue parseStyleValue(@NotNull XmlTag tag, @NotNull StyleResourceValue styleValue) {
    for (XmlTag child : tag.getSubTags()) {
      String name = getAttributeValue(child, ATTR_NAME);
      if (!StringUtil.isEmpty(name)) {
        String value = ValueXmlHelper.unescapeResourceString(ResourceHelper.getTextContent(child), true, true);
        ItemResourceValue itemValue = new ItemResourceValue(styleValue.getNamespace(), name, value, styleValue.getLibraryName());
        itemValue.setNamespaceResolver(ResourceHelper.getNamespaceResolver(child));
        styleValue.addItem(itemValue);
      }
    }

    return styleValue;
  }

  @NotNull
  private static AttrResourceValue parseAttrValue(@NotNull XmlTag tag, @NotNull AttrResourceValue attrValue) {
    for (XmlTag child : tag.getSubTags()) {
      String name = getAttributeValue(child, ATTR_NAME);
      if (name != null) {
        String value = getAttributeValue(child, ATTR_VALUE);
        if (value != null) {
          try {
            // Integer.decode/parseInt can't deal with hex value > 0x7FFFFFFF so we
            // use Long.decode instead.
            attrValue.addValue(name, (int)(long)Long.decode(value));
          } catch (NumberFormatException e) {
            // pass, we'll just ignore this value
          }
        }
      }
    }

    return attrValue;
  }

  private static ResourceValue parseArrayValue(@NotNull XmlTag tag, @NotNull ArrayResourceValue arrayValue) {
    for (XmlTag child : tag.getSubTags()) {
      String text = ValueXmlHelper.unescapeResourceString(ResourceHelper.getTextContent(child), true, true);
      arrayValue.addElement(text);
    }

    return arrayValue;
  }

  private static ResourceValue parsePluralsValue(@NotNull XmlTag tag, @NotNull PluralsResourceValue value) {
    for (XmlTag child : tag.getSubTags()) {
      String quantity = child.getAttributeValue(ATTR_QUANTITY);
      if (quantity != null) {
        String text = ValueXmlHelper.unescapeResourceString(ResourceHelper.getTextContent(child), true, true);
        value.addPlural(quantity, text);
      }
    }

    return value;
  }

  @NotNull
  private static ResourceValue parseValue(@NotNull XmlTag tag, @NotNull ResourceValue value) {
    String text = ResourceHelper.getTextContent(tag);
    text = ValueXmlHelper.unescapeResourceString(text, true, true);
    value.setValue(text);

    return value;
  }

  @NotNull
  private static PsiTextResourceValue parseTextValue(@NotNull XmlTag tag, @NotNull PsiTextResourceValue value) {
    String text = ResourceHelper.getTextContent(tag);
    text = ValueXmlHelper.unescapeResourceString(text, true, true);
    value.setValue(text);

    return value;
  }

  @Nullable
  PsiFile getPsiFile() {
    return myFile;
  }

  /** Clears the cached value, if any, and returns true if the value was cleared */
  public boolean recomputeValue() {
    if (myResourceValue != null) {
      // Force recompute in getResourceValue
      myResourceValue = null;
      return true;
    } else {
      return false;
    }
  }

  @Nullable
  public XmlTag getTag() {
    return myTag;

  }

  @Override
  public boolean equals(Object o) {
    // Only reference equality; we need to be able to distinguish duplicate elements which can happen during editing
    // for incremental updating to handle temporarily aliasing items.
    return this == o;
  }

  @Override
  public int hashCode() {
    return getName().hashCode();
  }

  @Override
  public String toString() {
    XmlTag tag = getTag();
    PsiFile file = getPsiFile();
    return super.toString() + ": " + (tag != null ? ResourceHelper.getTextContent(tag) : "null" + (file != null ? ":" + file.getName() : ""));
  }

  private class PsiTextResourceValue extends TextResourceValue {
    public PsiTextResourceValue(ResourceReference reference, String textValue, String rawXmlValue, String libraryName) {
      super(reference, textValue, rawXmlValue, libraryName);
    }

    @Override
    public String getRawXmlValue() {
      XmlTag tag = getTag();

      if (tag != null && tag.isValid()) {
        if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
          return ApplicationManager.getApplication().runReadAction((Computable<String>)() -> tag.getValue().getText());
        }
        return tag.getValue().getText();
      }
      else {
        return getValue();
      }
    }
  }
}
