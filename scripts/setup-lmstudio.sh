#!/bin/bash

################################################################################
# setup-lmstudio.sh
#
# Script para configurar LM Studio para testes de integração do JSimpleRag
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
LMSTUDIO_URL="http://localhost:1234"
LMSTUDIO_DOWNLOAD_MAC="https://lmstudio.ai/download/mac"
LMSTUDIO_DOWNLOAD_LINUX="https://lmstudio.ai/download/linux"
LMSTUDIO_DOWNLOAD_WINDOWS="https://lmstudio.ai/download/windows"

RECOMMENDED_MODELS=(
    "qwen2.5-7b-instruct"
    "nomic-embed-text"
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

print_step() {
    echo -e "${YELLOW}➤  $1${NC}"
}

################################################################################
# Check Functions
################################################################################

check_lmstudio_installed() {
    print_info "Checking if LM Studio is installed..."

    # Check common installation paths
    local lmstudio_paths=(
        "$HOME/Applications/LM Studio.app"
        "/Applications/LM Studio.app"
        "$HOME/.local/share/LM Studio"
        "/usr/share/lmstudio"
    )

    for path in "${lmstudio_paths[@]}"; do
        if [ -e "$path" ]; then
            print_success "LM Studio found at: $path"
            return 0
        fi
    done

    print_warning "LM Studio installation not detected"
    return 1
}

check_lmstudio_running() {
    print_info "Checking if LM Studio server is running..."

    if curl -s -f "$LMSTUDIO_URL/v1/models" > /dev/null 2>&1; then
        print_success "LM Studio server is running at $LMSTUDIO_URL"
        return 0
    else
        print_error "LM Studio server is not running"
        return 1
    fi
}

list_loaded_models() {
    local response=$(curl -s "$LMSTUDIO_URL/v1/models")

    if [ $? -eq 0 ] && [ ! -z "$response" ]; then
        echo "$response" | grep -o '"id":"[^"]*"' | cut -d'"' -f4
    fi
}

################################################################################
# Installation Functions
################################################################################

show_installation_instructions() {
    print_header "LM Studio Installation"

    print_info "LM Studio is a GUI application and must be installed manually."
    echo ""

    if [[ "$OSTYPE" == "darwin"* ]]; then
        print_step "For macOS:"
        echo "  1. Download from: $LMSTUDIO_DOWNLOAD_MAC"
        echo "  2. Open the .dmg file"
        echo "  3. Drag LM Studio to Applications folder"
        echo "  4. Launch LM Studio from Applications"
    elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
        print_step "For Linux:"
        echo "  1. Download from: $LMSTUDIO_DOWNLOAD_LINUX"
        echo "  2. Extract the archive"
        echo "  3. Run the LM Studio executable"
        echo "  4. (Optional) Add to PATH or create desktop shortcut"
    else
        print_step "For Windows (WSL):"
        echo "  1. Download from: $LMSTUDIO_DOWNLOAD_WINDOWS"
        echo "  2. Run the installer"
        echo "  3. Launch LM Studio from Start Menu"
        print_warning "Note: Server must be accessible from WSL at localhost:1234"
    fi

    echo ""
    print_info "After installation, return to this script and re-run it."
}

show_server_start_instructions() {
    print_header "Starting LM Studio Server"

    print_step "To start the LM Studio local server:"
    echo ""
    echo "  1. Open LM Studio application"
    echo "  2. Click on the '↔' icon in the left sidebar (Local Server)"
    echo "  3. Click the green 'Start Server' button"
    echo "  4. Ensure the server is running on port 1234"
    echo "  5. Leave LM Studio running in the background"
    echo ""
    print_info "The server status should show: 'Server running on http://localhost:1234'"
    echo ""

    read -p "Press Enter when the server is running..." -r
    echo ""
}

show_model_download_instructions() {
    print_header "Downloading Models in LM Studio"

    print_step "Recommended models for integration tests:"
    echo ""
    for model in "${RECOMMENDED_MODELS[@]}"; do
        echo "  • $model"
    done
    echo ""

    print_step "To download models:"
    echo ""
    echo "  1. In LM Studio, click the '🔍' icon (Search)"
    echo "  2. Search for each model name above"
    echo "  3. Click 'Download' next to the model"
    echo "  4. Wait for downloads to complete"
    echo ""

    print_info "Model details:"
    echo "  • qwen2.5-7b-instruct: ~4-5GB | Fast 7B model with good performance"
    echo "  • nomic-embed-text: ~274MB | Embeddings model (768 dimensions)"
    echo ""
    print_warning "Downloads may take several minutes depending on your connection."
    echo ""

    read -p "Press Enter when models are downloaded..." -r
    echo ""
}

show_model_load_instructions() {
    print_header "Loading Models in LM Studio"

    print_step "To load a model for use:"
    echo ""
    echo "  1. Go to 'Local Server' tab (↔ icon)"
    echo "  2. In the 'Select a model to load' dropdown, choose a model"
    echo "  3. Click 'Load Model' or select it"
    echo "  4. Wait for the model to load (shows in bottom status bar)"
    echo ""
    print_info "You can load different models as needed during testing."
    print_info "The integration tests will automatically use the loaded model."
    echo ""

    read -p "Press Enter when a model is loaded..." -r
    echo ""
}

################################################################################
# Verification Functions
################################################################################

verify_setup() {
    print_header "Verifying LM Studio Setup"

    local all_ok=true

    # Check if server is running
    if check_lmstudio_running; then
        print_success "Server is running"
    else
        print_error "Server is not running"
        all_ok=false
        return 1
    fi

    # Check loaded models
    print_info "Checking for loaded models..."
    local models=$(list_loaded_models)

    if [ -z "$models" ]; then
        print_warning "No models currently loaded"
        print_info "You can load models from the LM Studio UI"
        all_ok=false
    else
        print_success "Found loaded models:"
        echo "$models" | while read -r model; do
            echo "  • $model"
        done
    fi

    echo ""
    if [ "$all_ok" = true ]; then
        print_success "LM Studio is configured and ready!"
        return 0
    else
        print_warning "Setup incomplete. Please follow the instructions above."
        return 1
    fi
}

test_lmstudio() {
    print_header "Testing LM Studio"

    # Test models endpoint
    print_info "Testing /v1/models endpoint..."
    local models_response=$(curl -s "$LMSTUDIO_URL/v1/models")

    if echo "$models_response" | grep -q '"object":"list"'; then
        print_success "Models endpoint working"
    else
        print_error "Models endpoint failed"
        return 1
    fi

    # Test embeddings (if nomic-embed-text is loaded)
    print_info "Testing embeddings..."
    local embed_response=$(curl -s -X POST "$LMSTUDIO_URL/v1/embeddings" \
        -H "Content-Type: application/json" \
        -d '{"model":"nomic-embed-text","input":"Hello, world!"}' 2>/dev/null)

    if echo "$embed_response" | grep -q "embedding"; then
        print_success "Embeddings test passed"
    else
        print_warning "Embeddings test failed (nomic-embed-text may not be loaded)"
    fi

    # Test completions (if a completion model is loaded)
    print_info "Testing completions..."
    local completion_response=$(curl -s -X POST "$LMSTUDIO_URL/v1/chat/completions" \
        -H "Content-Type: application/json" \
        -d '{
            "model":"local-model",
            "messages":[{"role":"user","content":"What is 2+2? Answer with just the number."}],
            "max_tokens":10
        }' 2>/dev/null)

    if echo "$completion_response" | grep -q "content"; then
        print_success "Completions test passed"
        local answer=$(echo "$completion_response" | grep -o '"content":"[^"]*"' | head -1 | cut -d'"' -f4)
        print_info "Response: $answer"
    else
        print_warning "Completions test failed (no completion model may be loaded)"
    fi

    return 0
}

################################################################################
# Configuration Check
################################################################################

check_config_compatibility() {
    print_header "Checking Configuration Compatibility"

    # Check if Ollama is also setup
    if curl -s -f "http://localhost:11434/api/tags" > /dev/null 2>&1; then
        print_success "Ollama is also running (localhost:11434)"
        print_info "Dual-provider setup detected!"
    else
        print_info "Ollama not detected (you can set it up with ./setup-ollama.sh)"
    fi

    # Check application.properties
    local props_file="src/main/resources/application-integration-test.properties"
    if [ -f "$props_file" ]; then
        if grep -q "llmservice.provider2.api.url=http://localhost:1234" "$props_file"; then
            print_success "Integration test properties configured correctly"
        else
            print_warning "Integration test properties may need updating"
            print_info "Check: $props_file"
        fi
    else
        print_info "Integration test properties file not found (will be created during tests)"
    fi
}

################################################################################
# Main Script
################################################################################

main() {
    print_header "LM Studio Setup for JSimpleRag Integration Tests"

    echo "This script will guide you through:"
    echo "  1. Checking if LM Studio is installed"
    echo "  2. Starting the LM Studio server"
    echo "  3. Downloading and loading models"
    echo "  4. Verifying the setup"
    echo "  5. Running test requests"
    echo ""
    print_warning "Note: LM Studio is a GUI application and requires manual steps."
    echo ""

    # Step 1: Check Installation
    if ! check_lmstudio_installed; then
        show_installation_instructions
        exit 0
    fi

    # Step 2: Check if server is running
    if ! check_lmstudio_running; then
        show_server_start_instructions

        # Check again after user action
        if ! check_lmstudio_running; then
            print_error "Server still not detected. Please ensure it's running and try again."
            exit 1
        fi
    fi

    # Step 3: Guide model download
    print_info "Checking for models..."
    local models=$(list_loaded_models)

    if [ -z "$models" ]; then
        print_warning "No models detected"
        show_model_download_instructions
        show_model_load_instructions
    else
        print_success "Models found:"
        echo "$models" | while read -r model; do
            echo "  • $model"
        done
        echo ""

        # Check if recommended models are present
        local has_recommended=false
        for rec_model in "${RECOMMENDED_MODELS[@]}"; do
            if echo "$models" | grep -q "$rec_model"; then
                has_recommended=true
                break
            fi
        done

        if [ "$has_recommended" = false ]; then
            print_warning "Recommended models not found"
            read -p "Do you want to download recommended models? (y/n) " -n 1 -r
            echo
            if [[ $REPLY =~ ^[Yy]$ ]]; then
                show_model_download_instructions
                show_model_load_instructions
            fi
        fi
    fi

    # Step 4: Verify Setup
    if ! verify_setup; then
        print_error "Setup verification failed"
        exit 1
    fi

    # Step 5: Test
    read -p "Do you want to run connectivity tests? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        test_lmstudio
    fi

    # Step 6: Check compatibility
    check_config_compatibility

    # Done!
    print_header "Setup Complete!"

    echo ""
    print_success "LM Studio is configured and ready for JSimpleRag integration tests!"
    echo ""
    print_info "Next steps:"
    echo "  1. Run integration tests: mvn verify -P integration-tests"
    echo "  2. Check loaded models: curl $LMSTUDIO_URL/v1/models"
    echo "  3. Keep LM Studio running during tests"
    echo ""
    print_info "To setup Ollama as well, run: ./scripts/setup-ollama.sh"
    echo ""
    print_warning "Remember: Keep LM Studio application open with server running!"
    echo ""
}

# Run main function
main "$@"
