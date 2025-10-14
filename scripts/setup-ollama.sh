#!/bin/bash

################################################################################
# setup-ollama.sh
#
# Script para configurar Ollama para testes de integração do JSimpleRag
#
# Autor: Claude Code
# Data: 2025-10-14
################################################################################

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
OLLAMA_URL="http://localhost:11434"
REQUIRED_MODELS=(
    "tinyllama"
    "nomic-embed-text"
)
OPTIONAL_MODELS=(
    "llama2"
    "mistral"
)

################################################################################
# Helper Functions
################################################################################

print_header() {
    echo ""
    echo -e "${BLUE}════════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}════════════════════════════════════════════════════════════════${NC}"
    echo ""
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

################################################################################
# Check Functions
################################################################################

check_ollama_installed() {
    print_info "Checking if Ollama is installed..."

    if command -v ollama &> /dev/null; then
        OLLAMA_VERSION=$(ollama --version 2>&1 | head -n 1)
        print_success "Ollama is installed: $OLLAMA_VERSION"
        return 0
    else
        print_error "Ollama is not installed"
        return 1
    fi
}

check_ollama_running() {
    print_info "Checking if Ollama server is running..."

    if curl -s -f "$OLLAMA_URL/api/tags" > /dev/null 2>&1; then
        print_success "Ollama server is running at $OLLAMA_URL"
        return 0
    else
        print_error "Ollama server is not running"
        return 1
    fi
}

check_model_installed() {
    local model=$1

    if ollama list | grep -q "^$model"; then
        return 0
    else
        return 1
    fi
}

################################################################################
# Installation Functions
################################################################################

install_ollama() {
    print_header "Installing Ollama"

    print_info "Detecting operating system..."

    if [[ "$OSTYPE" == "linux-gnu"* ]]; then
        print_info "Installing Ollama for Linux..."
        curl -fsSL https://ollama.ai/install.sh | sh
    elif [[ "$OSTYPE" == "darwin"* ]]; then
        print_info "Installing Ollama for macOS..."
        if command -v brew &> /dev/null; then
            brew install ollama
        else
            print_warning "Homebrew not found. Please install manually from:"
            print_info "https://ollama.ai/download/mac"
            return 1
        fi
    else
        print_error "Unsupported OS: $OSTYPE"
        print_info "Please install manually from: https://ollama.ai/download"
        return 1
    fi

    print_success "Ollama installed successfully"
}

start_ollama_server() {
    print_header "Starting Ollama Server"

    print_info "Starting Ollama in background..."

    # Try to start Ollama
    if [[ "$OSTYPE" == "linux-gnu"* ]]; then
        # Linux: start as systemd service or background process
        if systemctl is-active --quiet ollama 2>/dev/null; then
            print_info "Ollama service already running"
        elif command -v systemctl &> /dev/null && systemctl list-unit-files | grep -q ollama; then
            sudo systemctl start ollama
            print_success "Started Ollama systemd service"
        else
            nohup ollama serve > /tmp/ollama.log 2>&1 &
            print_success "Started Ollama in background (PID: $!)"
        fi
    elif [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS: start as background process
        if pgrep -x "ollama" > /dev/null; then
            print_info "Ollama already running"
        else
            nohup ollama serve > /tmp/ollama.log 2>&1 &
            print_success "Started Ollama in background (PID: $!)"
        fi
    fi

    # Wait for server to be ready
    print_info "Waiting for Ollama server to be ready..."
    local max_attempts=30
    local attempt=0

    while [ $attempt -lt $max_attempts ]; do
        if curl -s -f "$OLLAMA_URL/api/tags" > /dev/null 2>&1; then
            print_success "Ollama server is ready!"
            return 0
        fi

        sleep 1
        ((attempt++))
        echo -n "."
    done

    echo ""
    print_error "Ollama server failed to start within 30 seconds"
    print_info "Check logs: tail -f /tmp/ollama.log"
    return 1
}

pull_model() {
    local model=$1
    local is_required=$2

    if check_model_installed "$model"; then
        print_success "Model '$model' is already installed"
        return 0
    fi

    print_info "Pulling model '$model'..."

    if [ "$is_required" = "true" ]; then
        print_warning "This is a REQUIRED model for integration tests"
    else
        print_info "This is an optional model"
    fi

    # Show model info
    case $model in
        tinyllama)
            print_info "Size: ~600MB | Speed: Very Fast | Use: Quick testing"
            ;;
        nomic-embed-text)
            print_info "Size: ~274MB | Speed: Fast | Use: Embeddings (768 dimensions)"
            ;;
        llama2)
            print_info "Size: ~3.8GB | Speed: Medium | Use: Better quality responses"
            ;;
        mistral)
            print_info "Size: ~4.1GB | Speed: Medium | Use: High quality responses"
            ;;
    esac

    if ollama pull "$model"; then
        print_success "Model '$model' installed successfully"
        return 0
    else
        if [ "$is_required" = "true" ]; then
            print_error "Failed to install REQUIRED model '$model'"
            return 1
        else
            print_warning "Failed to install optional model '$model' (continuing anyway)"
            return 0
        fi
    fi
}

################################################################################
# Verification Functions
################################################################################

verify_setup() {
    print_header "Verifying Setup"

    local all_ok=true

    # Check server
    if check_ollama_running; then
        print_success "Server check passed"
    else
        print_error "Server check failed"
        all_ok=false
    fi

    # Check required models
    print_info "Checking required models..."
    for model in "${REQUIRED_MODELS[@]}"; do
        if check_model_installed "$model"; then
            print_success "Required model '$model' is installed"
        else
            print_error "Required model '$model' is NOT installed"
            all_ok=false
        fi
    done

    # Check optional models
    print_info "Checking optional models..."
    for model in "${OPTIONAL_MODELS[@]}"; do
        if check_model_installed "$model"; then
            print_success "Optional model '$model' is installed"
        else
            print_warning "Optional model '$model' is not installed (recommended but not required)"
        fi
    done

    # List all installed models
    echo ""
    print_info "All installed models:"
    ollama list

    echo ""
    if [ "$all_ok" = true ]; then
        print_success "All checks passed! Ollama is ready for integration tests."
        return 0
    else
        print_error "Some checks failed. Please fix the issues above."
        return 1
    fi
}

test_ollama() {
    print_header "Testing Ollama"

    print_info "Testing embeddings with nomic-embed-text..."
    if echo '{"model": "nomic-embed-text", "prompt": "Hello, world!"}' | \
       curl -s -X POST "$OLLAMA_URL/api/embeddings" -d @- | grep -q "embedding"; then
        print_success "Embeddings test passed"
    else
        print_error "Embeddings test failed"
    fi

    print_info "Testing completion with tinyllama..."
    RESPONSE=$(echo '{"model": "tinyllama", "prompt": "What is 2+2?", "stream": false}' | \
               curl -s -X POST "$OLLAMA_URL/api/generate" -d @-)

    if echo "$RESPONSE" | grep -q "response"; then
        print_success "Completion test passed"
        print_info "Response: $(echo $RESPONSE | grep -o '"response":"[^"]*"' | head -c 100)..."
    else
        print_error "Completion test failed"
    fi
}

################################################################################
# Main Script
################################################################################

main() {
    print_header "Ollama Setup for JSimpleRag Integration Tests"

    echo "This script will:"
    echo "  1. Check if Ollama is installed"
    echo "  2. Install Ollama if needed"
    echo "  3. Start Ollama server"
    echo "  4. Pull required models for testing"
    echo "  5. Verify the setup"
    echo ""

    # Step 1: Check/Install Ollama
    if ! check_ollama_installed; then
        read -p "Do you want to install Ollama? (y/n) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            if ! install_ollama; then
                exit 1
            fi
        else
            print_error "Ollama is required for integration tests"
            exit 1
        fi
    fi

    # Step 2: Start Ollama Server
    if ! check_ollama_running; then
        if ! start_ollama_server; then
            exit 1
        fi
    fi

    # Step 3: Pull Required Models
    print_header "Installing Required Models"

    for model in "${REQUIRED_MODELS[@]}"; do
        if ! pull_model "$model" "true"; then
            print_error "Failed to install required model: $model"
            exit 1
        fi
    done

    # Step 4: Pull Optional Models (ask user)
    print_header "Optional Models"
    print_info "The following models are optional but recommended:"
    for model in "${OPTIONAL_MODELS[@]}"; do
        echo "  - $model"
    done
    echo ""

    read -p "Do you want to install optional models? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        for model in "${OPTIONAL_MODELS[@]}"; do
            pull_model "$model" "false" || true
        done
    fi

    # Step 5: Verify Setup
    if ! verify_setup; then
        exit 1
    fi

    # Step 6: Test
    read -p "Do you want to run a quick test? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        test_ollama
    fi

    # Done!
    print_header "Setup Complete!"

    echo ""
    print_success "Ollama is configured and ready for JSimpleRag integration tests!"
    echo ""
    print_info "Next steps:"
    echo "  1. Run integration tests: mvn verify -P integration-tests-ollama"
    echo "  2. Check server status: curl $OLLAMA_URL/api/tags"
    echo "  3. View logs: tail -f /tmp/ollama.log"
    echo ""
    print_info "To setup LM Studio as well, run: ./scripts/setup-lmstudio.sh"
    echo ""
}

# Run main function
main "$@"
