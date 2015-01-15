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

import java.io.File;

import com.edgytech.swingfast.FormDialog;
import com.edgytech.umongo.DocumentDeserializer.Format;
import com.mongodb.BasicDBObject;

/**
 *
 * @author antoine
 */
public class ImportDialog extends FormDialog {
  enum Item {

    inputFile, format, dropCollection, continueOnError, upsert, upsertFields, bulk, delimiter, quote, template
  }

  public ImportDialog() {
    setEnumBinding(Item.values(), null);
  }

  public DocumentDeserializer getDocumentDeserializer() {
    final DocumentDeserializer dd = new DocumentDeserializer(Format.values()[getIntFieldValue(Item.format)], null);
    dd.setFile(new File(getStringFieldValue(Item.inputFile)));
    dd.setDelimiter(getStringFieldValue(Item.delimiter));
    dd.setQuote(getStringFieldValue(Item.quote));
    dd.setTemplate((BasicDBObject) ((DocBuilderField) getBoundUnit(Item.template)).getDBObject());
    return dd;
  }

}
