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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.edgytech.swingfast.FormDialog;
import com.mongodb.MongoClientOptions;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;

/**
 *
 * @author antoine
 */
public class ConnectDialog extends FormDialog {

  enum Item {
    name, uri, servers, connectionMode, databases, user, password, connectionsPerHost, blockingThreadMultiplier, maxWaitTime, socketType, connectTimeout, socketTimeout, safeWrites, secondaryReads, proxyType, proxyHost, proxyPort, proxyUser, proxyPassword
  }

  public ConnectDialog() {
    setEnumBinding(Item.values(), null);
  }

  MongoClientOptions getMongoClientOptions() {
    final MongoClientOptions.Builder builder = MongoClientOptions.builder();
    builder.connectTimeout(getIntFieldValue(Item.connectTimeout));
    builder.socketTimeout(getIntFieldValue(Item.socketTimeout));
    if (!getBooleanFieldValue(Item.safeWrites)) {
      builder.writeConcern(WriteConcern.NONE);
    }

    if (getBooleanFieldValue(Item.secondaryReads)) {
      builder.readPreference(ReadPreference.secondaryPreferred());
    }

    final int stype = getIntFieldValue(Item.socketType);
    final int proxy = getIntFieldValue(Item.proxyType);
    if (proxy == 1) {
      // SOCKS proxy
      final String host = getStringFieldValue(Item.proxyHost);
      final int port = getIntFieldValue(Item.proxyPort);
      builder.socketFactory(new SocketFactory() {

        @Override
        public Socket createSocket() throws IOException {
          final SocketAddress addr = new InetSocketAddress(host, port);
          final Proxy proxy = new Proxy(Proxy.Type.SOCKS, addr);
          final Socket socket = new Socket(proxy);
          return socket;
        }

        @Override
        public Socket createSocket(final String string, final int i) throws IOException, UnknownHostException {
          final SocketAddress addr = new InetSocketAddress(host, port);
          final Proxy proxy = new Proxy(Proxy.Type.SOCKS, addr);
          final Socket socket = new Socket(proxy);
          final InetSocketAddress dest = new InetSocketAddress(string, i);
          socket.connect(dest);
          return socket;
        }

        @Override
        public Socket createSocket(final String string, final int i, final InetAddress ia, final int i1) throws IOException, UnknownHostException {
          throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public Socket createSocket(final InetAddress ia, final int i) throws IOException {
          final SocketAddress addr = new InetSocketAddress(host, port);
          final Proxy proxy = new Proxy(Proxy.Type.SOCKS, addr);
          final Socket socket = new Socket(proxy);
          final InetSocketAddress dest = new InetSocketAddress(ia, i);
          socket.connect(dest);
          return socket;
        }

        @Override
        public Socket createSocket(final InetAddress ia, final int i, final InetAddress ia1, final int i1) throws IOException {
          throw new UnsupportedOperationException("Not supported yet.");
        }
      });

    }

    if (stype == 1) {
      builder.socketFactory(SSLSocketFactory.getDefault());
    } else if (stype == 2) {
      // Create a trust manager that does not validate certificate chains
      final TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {

        @Override
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
          return null;
        }

        @Override
        public void checkClientTrusted(final java.security.cert.X509Certificate[] certs, final String authType) {
        }

        @Override
        public void checkServerTrusted(final java.security.cert.X509Certificate[] certs, final String authType) {
        }
      } };
      try {
        final SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        builder.socketFactory(sc.getSocketFactory());
      } catch (final Exception e) {
      }
    }

    return builder.build();
  }

  void setName(final String name) {
    setStringFieldValue(Item.name, name);
  }

  String getName() {
    return getStringFieldValue(Item.name);
  }
}
