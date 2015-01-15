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

import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bson.types.Binary;

import sun.misc.IOUtils;

import com.edgytech.swingfast.FileSelectorField;

/**
 *
 * @author antoine
 */
public class EditBinaryDialog extends EditFieldDialog {
  enum Item {
    inputFile
  }

  public EditBinaryDialog() {
    setEnumBinding(Item.values(), null);
  }

  @Override
  public Object getValue() {
    FileInputStream fis = null;
    try {
      final String path = ((FileSelectorField) getBoundComponentUnit(Item.inputFile)).getPath();
      fis = new FileInputStream(path);
      final byte[] bytes = IOUtils.readFully(fis, -1, true);
      return new Binary((byte) 0, bytes);
    } catch (final Exception ex) {
      getLogger().log(Level.WARNING, null, ex);
    } finally {
      try {
        fis.close();
      } catch (final IOException ex) {
        Logger.getLogger(EditBinaryDialog.class.getName()).log(Level.SEVERE, null, ex);
      }
    }
    return null;
  }

  @Override
  public void setValue(final Object value) {
    // nothing to do here...
  }
}
