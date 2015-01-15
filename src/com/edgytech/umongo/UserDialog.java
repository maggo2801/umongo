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

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.edgytech.swingfast.FormDialog;
import com.edgytech.swingfast.PasswordField;
import com.edgytech.swingfast.TextField;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.util.Util;

/**
 *
 * @author antoine
 */
public class UserDialog extends FormDialog {

  enum Item {

    user, password, userSource, read, readWrite, dbAdmin, userAdmin, clusterAdmin, readAnyDatabase, readWriteAnyDatabase, dbAdminAnyDatabase, userAdminAnyDatabase, version22
  }

  enum Role {
    read(Item.read), readWrite(Item.readWrite), dbAdmin(Item.dbAdmin), userAdmin(Item.userAdmin), clusterAdmin(Item.clusterAdmin), readAnyDatabase(Item.readAnyDatabase), readWriteAnyDatabase(Item.readWriteAnyDatabase), dbAdminAnyDatabase(Item.dbAdminAnyDatabase), userAdminAnyDatabase(Item.userAdminAnyDatabase);

    Role(final Item item) {
      this.item = item;
    }

    Item item;
  }

  public UserDialog() {
    setEnumBinding(Item.values(), null);
  }

  String _hash(final String username, final char[] passwd) {
    final ByteArrayOutputStream bout = new ByteArrayOutputStream(username.length() + 20 + passwd.length);
    try {
      bout.write(username.getBytes());
      bout.write(":mongo:".getBytes());
      for (final char element : passwd) {
        if (element >= 128) {
          throw new IllegalArgumentException("can't handle non-ascii passwords yet");
        }
        bout.write((byte) element);
      }
    } catch (final IOException ioe) {
      throw new RuntimeException("impossible", ioe);
    }
    return Util.hexMD5(bout.toByteArray());
  }

  void resetForEdit(final BasicDBObject user) {
    xmlLoadCheckpoint();

    setStringFieldValue(Item.user, user.getString(Item.user.name()));
    ((TextField) getBoundJComponentUnit(Item.user)).editable = false;

    ((PasswordField) getBoundJComponentUnit(Item.password)).nonEmpty = false;
    setStringFieldValue(Item.userSource, user.getString(Item.userSource.name()));

    final BasicDBList roles = (BasicDBList) user.get("roles");
    if (roles != null) {
      for (final Role role : Role.values()) {
        setBooleanFieldValue(role.item, roles.contains(role.name()));
      }
    } else {
      final boolean ro = user.getBoolean("readOnly");
      if (ro) {
        setBooleanFieldValue(Item.readWrite, true);
      } else {
        setBooleanFieldValue(Item.read, true);
      }
    }

    updateComponent();
  }

  void resetForNew() {
    xmlLoadCheckpoint();
  }

  BasicDBObject getUser(BasicDBObject userObj) {
    final String user = getStringFieldValue(Item.user);
    if (userObj == null) {
      userObj = new BasicDBObject("user", user);
    }

    // do not overwrite password if not set
    final String pass = getStringFieldValue(Item.password);
    if (!pass.isEmpty()) {
      userObj.put("pwd", _hash(user, pass.toCharArray()));
    }

    final String userSrc = getStringFieldValue(Item.userSource);
    if (!userSrc.trim().isEmpty()) {
      userObj.put(Item.userSource.name(), userSrc);
      // cant have pwd
      userObj.removeField("pwd");
    }

    if (!getBooleanFieldValue(Item.version22)) {
      // format from 2.4
      final BasicDBList roles = new BasicDBList();
      for (final Role role : Role.values()) {
        if (getBooleanFieldValue(role.item)) {
          roles.add(role.name());
        }
      }
      userObj.put("roles", roles);

      // readOnly flag must be dropped
      userObj.removeField("readOnly");
    } else {
      // keep it simple: if readWrite is not checked, then readOnly
      if (!getBooleanFieldValue(Item.readWrite)) {
        userObj.put("readOnly", true);
      }

      // remove roles
      userObj.removeField("roles");

      // all other flags should still be accepted
    }

    return userObj;
  }
}
