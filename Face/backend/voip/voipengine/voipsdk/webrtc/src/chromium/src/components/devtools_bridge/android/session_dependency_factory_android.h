// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef COMPONENTS_DEVTOOLS_BRIDGE_ANDROID_SESSION_DEPENDENCY_FACTORY_ANDROID_H_
#define COMPONENTS_DEVTOOLS_BRIDGE_ANDROID_SESSION_DEPENDENCY_FACTORY_ANDROID_H_

#include <jni.h>

#include "components/devtools_bridge/session_dependency_factory.h"

namespace devtools_bridge {
namespace android {

class SessionDependencyFactoryAndroid : public SessionDependencyFactory {
 public:
  SessionDependencyFactoryAndroid();
  virtual ~SessionDependencyFactoryAndroid();

  static bool RegisterNatives(JNIEnv* env);

  virtual scoped_ptr<AbstractPeerConnection> CreatePeerConnection(
      scoped_ptr<RTCConfiguration> config,
      scoped_ptr<AbstractPeerConnection::Delegate> delegate) override;

  virtual scoped_refptr<base::TaskRunner> signaling_thread_task_runner()
      override;
  virtual scoped_refptr<base::TaskRunner> io_thread_task_runner() override;

 private:
  const scoped_ptr<SessionDependencyFactory> impl_;

  DISALLOW_COPY_AND_ASSIGN(SessionDependencyFactoryAndroid);
};

}  // namespace android
}  // namespace devtools_bridge

#endif  // COMPONENTS_DEVTOOLS_BRIDGE_ANDROID_SESSION_DEPENDENCY_FACTORY_ANDROID_H_
