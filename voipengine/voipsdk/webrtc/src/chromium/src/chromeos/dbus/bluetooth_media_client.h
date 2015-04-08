// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef CHROMEOS_DBUS_BLUETOOTH_MEDIA_CLIENT_H_
#define CHROMEOS_DBUS_BLUETOOTH_MEDIA_CLIENT_H_

#include <memory>
#include <string>
#include <vector>

#include "base/callback.h"
#include "base/values.h"
#include "chromeos/chromeos_export.h"
#include "chromeos/dbus/dbus_client.h"
#include "dbus/object_path.h"

namespace chromeos {

// BluetoothMediaClient is used to communicate with the Media interface of a
// local Bluetooth adapter.
class CHROMEOS_EXPORT BluetoothMediaClient : public DBusClient {
 public:
  // Properties used to register a Media Endpoint.
  struct CHROMEOS_EXPORT EndpointProperties {
    EndpointProperties();
    ~EndpointProperties();

    // UUID of the profile implemented by the endpoint.
    std::string uuid;

    // Assigned codec value supported by the endpoint. The byte should match the
    // codec specification indicated by the UUID.
    // Since SBC codec is mandatory for A2DP, the default value of codec should
    // be 0x00.
    uint8_t codec;

    // Capabilities of the endpoints. The order of bytes should match the bit
    // arrangement in the specification indicated by the UUID.
    std::vector<uint8_t> capabilities;
  };

  ~BluetoothMediaClient() override;

  // The ErrorCallback is used by media API methods to indicate failure.
  // It receives two arguments: the name of the error in |error_name| and
  // an optional message in |error_message|.
  typedef base::Callback<void(const std::string& error_name,
                              const std::string& error_message)> ErrorCallback;

  // Registers a media endpoint to sender at the D-Bus object path
  // |endpoint_path|. |properties| specifies profile UUID which the endpoint is
  // for, Codec implemented by the endpoint and the Capabilities of the
  // endpoint.
  virtual void RegisterEndpoint(const dbus::ObjectPath& object_path,
                                const dbus::ObjectPath& endpoint_path,
                                const EndpointProperties& properties,
                                const base::Closure& callback,
                                const ErrorCallback& error_callback) = 0;

  // Unregisters the media endpoint with the D-Bus object path |endpoint_path|.
  virtual void UnregisterEndpoint(const dbus::ObjectPath& object_path,
                                  const dbus::ObjectPath& endpoint_path,
                                  const base::Closure& callback,
                                  const ErrorCallback& error_callback) = 0;

  // TODO(mcchou): The RegisterPlayer and UnregisterPlayer methods are not
  // included, since they are not used. These two methods may be added later.

  static BluetoothMediaClient* Create();

  // Constants used to indicate exceptional error conditions.
  static const char kNoResponseError[];

 protected:
  BluetoothMediaClient();

 private:
  DISALLOW_COPY_AND_ASSIGN(BluetoothMediaClient);
};

}  // namespace chromeos

#endif  // CHROMEOS_DBUS_BLUETOOTH_MEDIA_CLIENT_H_
