// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef CHROMECAST_MEDIA_CDM_BROWSER_CDM_CAST_H_
#define CHROMECAST_MEDIA_CDM_BROWSER_CDM_CAST_H_

#include <stdint.h>

#include <map>
#include <string>

#include "base/callback.h"
#include "base/macros.h"
#include "base/memory/ref_counted.h"
#include "media/base/browser_cdm.h"

namespace chromecast {
namespace media {
class DecryptContext;

// BrowserCdmCast is an extension of BrowserCdm that provides common
// functionality across CDM implementations.
// All these additional functions are synchronous so:
// - either both the CDM and the media pipeline must be running on the same
//   thread,
// - or BrowserCdmCast implementations must use some locks.
//
class BrowserCdmCast : public ::media::BrowserCdm {
 public:
  BrowserCdmCast();
  virtual ~BrowserCdmCast() override;

  // PlayerTracker implementation.
  int RegisterPlayer(const base::Closure& new_key_cb,
                     const base::Closure& cdm_unset_cb) override;
  void UnregisterPlayer(int registration_id) override;

  // Returns the decryption context needed to decrypt frames encrypted with
  // |key_id|.
  // Returns null if |key_id| is not available.
  virtual scoped_refptr<DecryptContext> GetDecryptContext(
      const std::string& key_id) const = 0;

 protected:
  // Notifies all listeners a new key was added on the next message loop cycle.
  void NotifyKeyAdded() const;

 private:
  uint32_t next_registration_id_;
  std::map<uint32_t, base::Closure> new_key_callbacks_;
  std::map<uint32_t, base::Closure> cdm_unset_callbacks_;

  DISALLOW_COPY_AND_ASSIGN(BrowserCdmCast);
};

}  // namespace media
}  // namespace chromecast

#endif  // CHROMECAST_MEDIA_CDM_BROWSER_CDM_CAST_H_
