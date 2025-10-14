#!/bin/bash

################################################################################
# check-providers.sh
#
# Script para verificar status dos provedores LLM para testes de integração
#
# Autor: Claude Code
# Data: 2025-10-14
################################################################################

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration
OLLAMA_URL="http://localhost:11434"
LMSTUDIO_URL="http://localhost:1234"

################################################################################
# Helper Functions
################################################################################

print_header() {
    echo ""
    echo -e "${CYAN}╔════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║  $1${NC}"
    echo -e "${CYAN}╚════════════════════════════════════════════════════════════════╝${NC}"
    echo ""
}

print_section() {
    echo ""
    echo -e "${BLUE}▶ $1${NC}"
    echo -e "${BLUE}  $(printf '─%.0s' {1..60})${NC}"
}

print_success() {
    echo -e "  ${GREEN}✅ $1${NC}"
}

print_error() {
    echo -e "  ${RED}❌ $1${NC}"
}

print_warning() {
    echo -e "  ${YELLOW}⚠️  $1${NC}"
}

print_info() {
    echo -e "  ${BLUE}ℹ️  $1${NC}"
}

print_stat() {
    printf "  %-30s ${CYAN}%s${NC}\n" "$1" "$2"
}

################################################################################
# Check Functions
################################################################################

check_ollama() {
    print_section "Ollama (localhost:11434)"

    # Check if running
    if curl -s -f --max-time 2 "$OLLAMA_URL/api/tags" > /dev/null 2>&1; then
        print_success "Server is running"

        # Get version
        local version=$(curl -s "$OLLAMA_URL/api/version" 2>/dev/null | grep -o '"version":"[^"]*"' | cut -d'"' -f4)
        if [ ! -z "$version" ]; then
            print_stat "Version:" "$version"
        fi

        # List models
        local models=$(curl -s "$OLLAMA_URL/api/tags" 2>/dev/null)
        if [ ! -z "$models" ]; then
            local model_count=$(echo "$models" | grep -o '"name"' | wc -l)
            print_stat "Models installed:" "$model_count"

            # List model names
            local model_names=$(echo "$models" | grep -o '"name":"[^"]*"' | cut -d'"' -f4 | head -10)
            if [ ! -z "$model_names" ]; then
                echo ""
                print_info "Available models:"
                echo "$model_names" | while read -r model; do
                    echo "    • $model"
                done
            fi
        fi

        # Check required models
        echo ""
        print_info "Required models check:"
        local all_required=true

        if echo "$models" | grep -q '"name":"tinyllama"'; then
            print_success "tinyllama (required) ✓"
        else
            print_error "tinyllama (required) ✗"
            all_required=false
        fi

        if echo "$models" | grep -q '"name":"nomic-embed-text"'; then
            print_success "nomic-embed-text (required) ✓"
        else
            print_error "nomic-embed-text (required) ✗"
            all_required=false
        fi

        if [ "$all_required" = false ]; then
            echo ""
            print_warning "Install missing models: ./scripts/setup-ollama.sh"
        fi

        return 0
    else
        print_error "Server is not running"
        print_info "Start with: ollama serve"
        print_info "Or run: ./scripts/setup-ollama.sh"
        return 1
    fi
}

check_lmstudio() {
    print_section "LM Studio (localhost:1234)"

    # Check if running
    if curl -s -f --max-time 2 "$LMSTUDIO_URL/v1/models" > /dev/null 2>&1; then
        print_success "Server is running"

        # List models
        local models=$(curl -s "$LMSTUDIO_URL/v1/models" 2>/dev/null)
        if [ ! -z "$models" ]; then
            local model_count=$(echo "$models" | grep -o '"id"' | wc -l)
            print_stat "Models loaded:" "$model_count"

            # List model IDs
            local model_ids=$(echo "$models" | grep -o '"id":"[^"]*"' | cut -d'"' -f4)
            if [ ! -z "$model_ids" ]; then
                echo ""
                print_info "Loaded models:"
                echo "$model_ids" | while read -r model; do
                    echo "    • $model"
                done
            fi
        else
            print_warning "No models loaded"
            print_info "Load models from LM Studio UI"
        fi

        # Check recommended models
        echo ""
        print_info "Recommended models check:"

        if echo "$models" | grep -qi "qwen"; then
            print_success "qwen (recommended) ✓"
        else
            print_warning "qwen (recommended) ✗"
        fi

        if echo "$models" | grep -qi "nomic-embed"; then
            print_success "nomic-embed-text (recommended) ✓"
        else
            print_warning "nomic-embed-text (recommended) ✗"
        fi

        return 0
    else
        print_error "Server is not running"
        print_info "Start from LM Studio UI: Local Server → Start Server"
        print_info "Or run: ./scripts/setup-lmstudio.sh"
        return 1
    fi
}

################################################################################
# Test Functions
################################################################################

test_ollama_embeddings() {
    print_section "Testing Ollama Embeddings"

    local response=$(curl -s -X POST "$OLLAMA_URL/api/embeddings" \
        -d '{"model":"nomic-embed-text","prompt":"Test"}' 2>/dev/null)

    if echo "$response" | grep -q "embedding"; then
        local embedding_size=$(echo "$response" | grep -o '\[[0-9.,-]*\]' | tr ',' '\n' | wc -l)
        print_success "Embeddings working (dimension: $embedding_size)"
        return 0
    else
        print_error "Embeddings test failed"
        return 1
    fi
}

test_ollama_completion() {
    print_section "Testing Ollama Completion"

    local response=$(curl -s -X POST "$OLLAMA_URL/api/generate" \
        -d '{"model":"tinyllama","prompt":"What is 2+2?","stream":false}' 2>/dev/null)

    if echo "$response" | grep -q "response"; then
        print_success "Completion working"
        local answer=$(echo "$response" | grep -o '"response":"[^"]*"' | cut -d'"' -f4 | head -c 50)
        print_info "Sample response: $answer..."
        return 0
    else
        print_error "Completion test failed"
        return 1
    fi
}

test_lmstudio_embeddings() {
    print_section "Testing LM Studio Embeddings"

    local response=$(curl -s -X POST "$LMSTUDIO_URL/v1/embeddings" \
        -H "Content-Type: application/json" \
        -d '{"model":"nomic-embed-text","input":"Test"}' 2>/dev/null)

    if echo "$response" | grep -q "embedding"; then
        local embedding_size=$(echo "$response" | grep -o '\[[0-9.,-]*\]' | head -1 | tr ',' '\n' | wc -l)
        print_success "Embeddings working (dimension: $embedding_size)"
        return 0
    else
        print_warning "Embeddings test failed (model may not be loaded)"
        return 1
    fi
}

test_lmstudio_completion() {
    print_section "Testing LM Studio Completion"

    local response=$(curl -s -X POST "$LMSTUDIO_URL/v1/chat/completions" \
        -H "Content-Type: application/json" \
        -d '{
            "model":"local-model",
            "messages":[{"role":"user","content":"What is 2+2?"}],
            "max_tokens":50
        }' 2>/dev/null)

    if echo "$response" | grep -q "content"; then
        print_success "Completion working"
        local answer=$(echo "$response" | grep -o '"content":"[^"]*"' | head -1 | cut -d'"' -f4 | head -c 50)
        print_info "Sample response: $answer..."
        return 0
    else
        print_warning "Completion test failed (model may not be loaded)"
        return 1
    fi
}

################################################################################
# Summary Functions
################################################################################

show_summary() {
    print_header "Summary"

    local ollama_ok=$1
    local lmstudio_ok=$2

    # Overall status
    if [ "$ollama_ok" = "0" ] && [ "$lmstudio_ok" = "0" ]; then
        echo -e "${GREEN}╔════════════════════════════════════════════════════════╗${NC}"
        echo -e "${GREEN}║  ✅ ALL SYSTEMS GO!                                    ║${NC}"
        echo -e "${GREEN}║  Both providers are ready for integration tests       ║${NC}"
        echo -e "${GREEN}╚════════════════════════════════════════════════════════╝${NC}"
        echo ""
        print_success "You can run: mvn verify -P integration-tests"
        return 0
    elif [ "$ollama_ok" = "0" ]; then
        echo -e "${YELLOW}╔════════════════════════════════════════════════════════╗${NC}"
        echo -e "${YELLOW}║  ⚠️  PARTIAL SETUP                                     ║${NC}"
        echo -e "${YELLOW}║  Ollama ready, LM Studio not available                ║${NC}"
        echo -e "${YELLOW}╚════════════════════════════════════════════════════════╝${NC}"
        echo ""
        print_success "You can run: mvn verify -P integration-tests-ollama"
        print_info "For full tests, setup LM Studio: ./scripts/setup-lmstudio.sh"
        return 1
    elif [ "$lmstudio_ok" = "0" ]; then
        echo -e "${YELLOW}╔════════════════════════════════════════════════════════╗${NC}"
        echo -e "${YELLOW}║  ⚠️  PARTIAL SETUP                                     ║${NC}"
        echo -e "${YELLOW}║  LM Studio ready, Ollama not available                ║${NC}"
        echo -e "${YELLOW}╚════════════════════════════════════════════════════════╝${NC}"
        echo ""
        print_warning "Ollama recommended for basic tests"
        print_info "Setup Ollama: ./scripts/setup-ollama.sh"
        return 1
    else
        echo -e "${RED}╔════════════════════════════════════════════════════════╗${NC}"
        echo -e "${RED}║  ❌ PROVIDERS NOT AVAILABLE                            ║${NC}"
        echo -e "${RED}║  Both providers need to be configured                  ║${NC}"
        echo -e "${RED}╚════════════════════════════════════════════════════════╝${NC}"
        echo ""
        print_error "Cannot run integration tests"
        print_info "Setup Ollama: ./scripts/setup-ollama.sh"
        print_info "Setup LM Studio: ./scripts/setup-lmstudio.sh"
        return 1
    fi
}

show_test_commands() {
    print_section "Available Test Commands"

    echo ""
    echo "  Based on current setup, you can run:"
    echo ""

    if [ "$1" = "0" ] && [ "$2" = "0" ]; then
        echo "  # Full integration tests (Ollama + LM Studio)"
        echo "  mvn verify -P integration-tests"
        echo ""
        echo "  # Just Ollama tests"
        echo "  mvn verify -P integration-tests-ollama"
        echo ""
        echo "  # Multi-provider tests"
        echo "  mvn verify -P multi-provider-tests"
    elif [ "$1" = "0" ]; then
        echo "  # Ollama only tests"
        echo "  mvn verify -P integration-tests-ollama"
    fi

    echo ""
    echo "  # Unit tests only (always available)"
    echo "  mvn test"
    echo ""
}

################################################################################
# Main Script
################################################################################

main() {
    print_header "LLM Provider Status Check - JSimpleRag"

    local ollama_status=1
    local lmstudio_status=1

    # Check Ollama
    if check_ollama; then
        ollama_status=0
    fi

    # Check LM Studio
    if check_lmstudio; then
        lmstudio_status=0
    fi

    # Run tests if requested
    if [ "$1" = "--test" ] || [ "$1" = "-t" ]; then
        if [ "$ollama_status" = "0" ]; then
            test_ollama_embeddings
            test_ollama_completion
        fi

        if [ "$lmstudio_status" = "0" ]; then
            test_lmstudio_embeddings
            test_lmstudio_completion
        fi
    fi

    # Show summary
    show_summary $ollama_status $lmstudio_status
    local summary_result=$?

    # Show test commands
    show_test_commands $ollama_status $lmstudio_status

    # Exit with appropriate code
    exit $summary_result
}

# Parse arguments
if [ "$1" = "--help" ] || [ "$1" = "-h" ]; then
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -h, --help     Show this help message"
    echo "  -t, --test     Run connectivity tests"
    echo ""
    echo "Examples:"
    echo "  $0              # Check provider status"
    echo "  $0 --test       # Check status and run tests"
    exit 0
fi

# Run main
main "$@"
