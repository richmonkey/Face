// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef CHROME_BROWSER_CHROMEOS_VPN_VPN_SERVICE_FACTORY_H_
#define CHROME_BROWSER_CHROMEOS_VPN_VPN_SERVICE_FACTORY_H_

#include "base/macros.h"
#include "components/keyed_service/content/browser_context_keyed_service_factory.h"

namespace content {

class BrowserContext;

}  // namespace content

template <typename T>
struct DefaultSingletonTraits;

namespace chromeos {

class VpnService;

// Factory to create VpnService.
class VpnServiceFactory : public BrowserContextKeyedServiceFactory {
 public:
  static VpnService* GetForBrowserContext(content::BrowserContext* context);
  static VpnServiceFactory* GetInstance();

 private:
  friend struct DefaultSingletonTraits<VpnServiceFactory>;

  VpnServiceFactory();
  ~VpnServiceFactory() override;

  // BrowserContextKeyedServiceFactory:
  bool ServiceIsCreatedWithBrowserContext() const override;
  KeyedService* BuildServiceInstanceFor(
      content::BrowserContext* context) const override;

  DISALLOW_COPY_AND_ASSIGN(VpnServiceFactory);
};

}  // namespace chromeos

#endif  // CHROME_BROWSER_CHROMEOS_VPN_VPN_SERVICE_FACTORY_H_
