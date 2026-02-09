require 'json'

package = JSON.parse(File.read(File.join(__dir__, 'package.json')))

Pod::Spec.new do |s|
  s.name           = 'ExpoBraintree'
  s.version        = package['version']
  s.summary        = package['description']
  s.description    = package['description']
  s.license        = package['license']
  s.author         = package['author']
  s.homepage       = package['homepage'] || 'https://github.com/placeholder/expo-braintree'
  s.platforms      = { :ios => '16.0' }
  s.swift_version  = '5.10'
  s.source         = { git: '' }
  s.static_framework = true

  s.dependency 'ExpoModulesCore'
  s.dependency 'BraintreeCore',     '~> 7.3'
  s.dependency 'BraintreeCard',     '~> 7.3'
  s.dependency 'BraintreeApplePay', '~> 7.3'
  s.dependency 'BraintreePayPal',   '~> 7.3'
  s.dependency 'BraintreeVenmo',    '~> 7.3'

  s.source_files = 'ios/**/*.swift'
end
