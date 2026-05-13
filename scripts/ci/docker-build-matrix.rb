#!/usr/bin/env ruby
# frozen_string_literal: true

require 'yaml'
require 'json'

compose_file = ENV.fetch('COMPOSE_FILE', 'docker-compose.yml')
context_prefix = ENV['CONTEXT_PREFIX']

compose = YAML.safe_load(File.read(compose_file), aliases: true) || {}
services = compose.fetch('services', {})

entries = []

services.each do |service_name, service_cfg|
  build_cfg = service_cfg['build']
  next unless build_cfg

  context = nil
  dockerfile = nil

  if build_cfg.is_a?(String)
    context = build_cfg
  elsif build_cfg.is_a?(Hash)
    context = build_cfg['context']
    dockerfile = build_cfg['dockerfile']
  end

  next if context.nil? || context.strip.empty?

  normalized_context = context.sub(%r{^\./}, '')
  next if context_prefix && !normalized_context.start_with?(context_prefix)

  resolved_dockerfile = if dockerfile && !dockerfile.strip.empty?
    dockerfile
  else
    "#{context}/Dockerfile"
  end

  entries << {
    'service' => service_name,
    'context' => context,
    'dockerfile' => resolved_dockerfile
  }
end

puts JSON.generate(entries)
