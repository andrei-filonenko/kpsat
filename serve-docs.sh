#!/bin/bash
echo "Serving docs at http://localhost:8000"
echo "  - DSL:    http://localhost:8000/docs/dsl/"
echo "  - Solver: http://localhost:8000/docs/solver/"
echo "Press Ctrl+C to stop"
python3 -m http.server 8000

