/**
 *      Copyright (C) 2010 EdgyTech LLC.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.edgytech.umongo;

import java.util.logging.Level;

import com.edgytech.swingfast.ButtonBase;
import com.edgytech.swingfast.Div;
import com.edgytech.swingfast.EnumListener;
import com.edgytech.swingfast.XmlComponentUnit;
import com.edgytech.umongo.DocFieldText.Item;

/**
 *
 * @author antoine
 */
public class DocFieldText extends Div implements EnumListener<Item> {

  enum Item {
    fields, value, edit, up, down, remove, addField, addKey, addType
  }

  DocFieldObject _object;
  String key;
  Object value;

  /**
   * Creates a new instance of FieldFile
   */
  public DocFieldText() {
    try {
      xmlLoad(Resource.getXmlDir(), Resource.File.docFieldText, null);
    } catch (final Exception ex) {
      getLogger().log(Level.SEVERE, null, ex);
    }
    setEnumBinding(Item.values(), this);
  }

  /**
   * Creates a new instance of FieldFile
   */
  public DocFieldText(final String id, final String key, final Object value, final DocFieldObject object) {
    this();
    setId(id);
    setLabel(key);
    this.key = key;
    this.value = value;
    _object = object;
    setStringFieldValue(Item.value, MongoUtils.getJSONPreview(value));
    if (value == null) {
      getJComponentBoundUnit(Item.edit).visible = false;
    }
  }

  public void edit(final ButtonBase<?, ?> button) {
    value = UMongo.instance.getGlobalStore().editValue(key, value);
    setStringFieldValue(Item.value, MongoUtils.getJSONPreview(value));
    updateComponent();
    _object.commitComponent();
  }

  public void remove(final ButtonBase<?, ?> button) {
    _object.remove(key);
  }

  public void moveUp(final ButtonBase<?, ?> button) {
    _object.moveUp(key);
  }

  public void moveDown(final ButtonBase<?, ?> button) {
    _object.moveDown(key);
  }

  @Override
  public void actionPerformed(final Item enm, final XmlComponentUnit unit, final Object src) {
  }

  public Object getValue() {
    return value;
  }

  public String getKey() {
    return key;
  }

}
