// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef CONTENT_BROWSER_PERMISSIONS_PERMISSION_SERVICE_CONTEXT_H_
#define CONTENT_BROWSER_PERMISSIONS_PERMISSION_SERVICE_CONTEXT_H_

#include "base/macros.h"
#include "base/memory/scoped_vector.h"
#include "content/public/browser/web_contents_observer.h"
#include "mojo/public/cpp/bindings/interface_request.h"

namespace content {

class PermissionService;
class PermissionServiceImpl;
class RenderFrameHost;

// Provides information to a PermissionService. It is used by the
// PermissionService to handle request permission UI.
// There is one PermissionServiceContext per RenderFrameHost/RenderProcessHost
// which owns it. It then owns all PermissionService associated to their owner.
class PermissionServiceContext : public WebContentsObserver {
 public:
  explicit PermissionServiceContext(RenderFrameHost* render_frame_host);
  virtual ~PermissionServiceContext();

  void CreateService(mojo::InterfaceRequest<PermissionService> request);

  // Called by a PermissionService identified as |service| when it has a
  // connection error in order to get unregistered and killed.
  void ServiceHadConnectionError(PermissionServiceImpl* service);

 private:
  // WebContentsObserver
  void RenderFrameDeleted(RenderFrameHost* render_frame_host) override;
  void DidNavigateAnyFrame(RenderFrameHost* render_frame_host,
                           const LoadCommittedDetails& details,
                           const FrameNavigateParams& params) override;

  void CancelPendingRequests(RenderFrameHost*) const;

  RenderFrameHost* render_frame_host_;
  ScopedVector<PermissionServiceImpl> services_;

  DISALLOW_COPY_AND_ASSIGN(PermissionServiceContext);
};

}  // namespace content

#endif  // CONTENT_BROWSER_PERMISSIONS_PERMISSION_SERVICE_CONTEXT_H_
