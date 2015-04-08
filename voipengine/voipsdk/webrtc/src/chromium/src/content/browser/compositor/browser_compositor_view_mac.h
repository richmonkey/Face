// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef CONTENT_BROWSER_COMPOSITOR_BROWSER_COMPOSITOR_VIEW_MAC_H_
#define CONTENT_BROWSER_COMPOSITOR_BROWSER_COMPOSITOR_VIEW_MAC_H_

#include "content/browser/compositor/browser_compositor_ca_layer_tree_mac.h"
#include "ui/compositor/compositor.h"

namespace content {

// A ui::Compositor and a gfx::AcceleratedWidget (and helper) that it draws
// into. This structure is used to efficiently recycle these structures across
// tabs (because creating a new ui::Compositor for each tab would be expensive
// in terms of time and resources).
class BrowserCompositorMac {
 public:
  // Create a compositor, or recycle a preexisting one.
  static scoped_ptr<BrowserCompositorMac> Create();

  // Delete a compositor, or allow it to be recycled.
  static void Recycle(scoped_ptr<BrowserCompositorMac> compositor);

  ui::Compositor* compositor() { return &compositor_; }
  AcceleratedWidgetMac* accelerated_widget_mac() {
    return &accelerated_widget_mac_;
  }

 private:
  BrowserCompositorMac();

  AcceleratedWidgetMac accelerated_widget_mac_;
  ui::Compositor compositor_;

  DISALLOW_COPY_AND_ASSIGN(BrowserCompositorMac);
};

// A class to keep around whenever a BrowserCompositorMac may be created.
// While at least one instance of this class exists, a spare
// BrowserCompositorViewCocoa will be kept around to be recycled so that the
// next BrowserCompositorMac to be created will be be created quickly.
class BrowserCompositorMacPlaceholder {
 public:
  BrowserCompositorMacPlaceholder();
  ~BrowserCompositorMacPlaceholder();

 private:
  DISALLOW_COPY_AND_ASSIGN(BrowserCompositorMacPlaceholder);
};

}  // namespace content

#endif  // CONTENT_BROWSER_COMPOSITOR_BROWSER_COMPOSITOR_VIEW_MAC_H_
