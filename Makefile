# Dependencies

.PHONY: bootstrap
bootstrap:
	brew install tokei

# Project info

.PHONY: loc
loc:
	tokei ./common/src/ ./storefront/src/ ./barista/src/ ./inventory/src/
