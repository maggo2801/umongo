/**
 * Copyright (C) 2010 EdgyTech LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.edgytech.umongo;

import com.edgytech.swingfast.ButtonBase;
import com.edgytech.swingfast.EnumListener;
import com.edgytech.swingfast.FormDialog;
import com.edgytech.swingfast.XmlComponentUnit;
import com.google.gson.JsonElement;

/**
 *
 * @author antoine
 */
public class JSONTextDialog extends FormDialog implements EnumListener<JSONTextDialog.Item> {

  enum Item {
    expandTextArea, convertFromJS, indent, help
  }

  public JSONTextDialog() {
    setEnumBinding(Item.values(), this);
  }

  public void setText(final String text) {
    setStringFieldValue(Item.expandTextArea, text);
  }

  public String getText() {
    return getStringFieldValue(Item.expandTextArea);
  }

  public void convertFromJS(final ButtonBase button) {
    String txt = getComponentStringFieldValue(Item.expandTextArea);
    txt = txt.replaceAll("ISODate\\(([^\\)]*)\\)", "{ \"\\$date\": $1 }");
    txt = txt.replaceAll("ObjectId\\(([^\\)]*)\\)", "{ \"\\$oid\": $1 }");
    txt = txt.replaceAll("NumberLong\\(([^\\)]*)\\)", "$1");
    // txt = txt.replaceAll("ISODate", "\\$date");
    setComponentStringFieldValue(Item.expandTextArea, txt);
  }

  public void indent(final ButtonBase button) {
    final String txt = getComponentStringFieldValue(Item.expandTextArea);
    final JsonElement je = MongoUtils.getJsonParser().parse(txt);
    final String prettyJsonString = MongoUtils.getGson().toJson(je);
    setComponentStringFieldValue(Item.expandTextArea, prettyJsonString);
  }

  @Override
  public void actionPerformed(final Item enm, final XmlComponentUnit unit, final Object src) {
  }

}
