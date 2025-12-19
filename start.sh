#!/bin/bash

# Ensure we are in the project root
cd "$(dirname "$0")"

echo "Checking dependencies..."

# Check if root node_modules exists
if [ ! -d "node_modules" ]; then
    echo "Root node_modules not found. Installing dependencies..."
    npm install
fi

# Check if frontend node_modules exists
if [ ! -d "frontend/node_modules" ]; then
    echo "Frontend node_modules not found. Installing dependencies..."
    cd frontend && npm install && cd ..
fi

echo "Starting Violin Practice Assistant..."
npm run dev
