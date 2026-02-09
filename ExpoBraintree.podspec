require 'json'

package = JSON.parse(File.read(File.join(__dir__, 'package.json')))

Pod::Spec.new do |s|
  s.name           = 'ExpoBraintree'
  s.version        = package['version']
  s.summary        = package['description']
  s.description    = "#{package['description']}\n"
  s.license        = package['license']
  s.author         = package['author'] || 'TickPick'
  s.homepage       = package['homepage'] || 'https://github.com/tickpick/expo-braintree'
  s.platforms      = { :ios => '16.0' }
  s.swift_version  = '5.10'
  s.source         = { git: 'https://github.com/tickpick/expo-braintree.git', tag: s.version.to_s }
  s.static_framework = true

  s.dependency 'ExpoModulesCore'
  s.dependency 'Braintree',           '~> 7.3'
  s.dependency 'Braintree/ApplePay',  '~> 7.3'
  s.dependency 'Braintree/PayPal',    '~> 7.3'
  s.dependency 'Braintree/Venmo',     '~> 7.3'

  s.source_files = 'ios/**/*.swift'
end
