#!/usr/bin/env python3
"""
Python Service Manager for SkyPulse Analytics
Handles automatic dependency installation and service management
"""

import sys
import subprocess
import os
import json
import importlib
import pkg_resources
from pathlib import Path

class PythonServiceManager:
    def __init__(self):
        self.script_dir = Path(__file__).parent
        self.requirements_file = self.script_dir / "requirements.txt"
        self.setup_complete_file = self.script_dir / ".setup_complete"
        
    def check_python_version(self):
        """Check if Python version is compatible"""
        version = sys.version_info
        if version.major < 3 or (version.major == 3 and version.minor < 7):
            raise RuntimeError(f"Python 3.7+ required, but found {version.major}.{version.minor}")
        return True
    
    def get_required_packages(self):
        """Get list of required packages from requirements.txt"""
        if not self.requirements_file.exists():
            return [
                "pandas>=1.3.0",
                "numpy>=1.21.0",
                "matplotlib>=3.5.0",
                "seaborn>=0.11.0",
                "reportlab>=3.6.0",
                "PyMySQL>=1.0.0",
                "SQLAlchemy>=1.4.0"
            ]
        
        with open(self.requirements_file, 'r') as f:
            return [line.strip() for line in f if line.strip() and not line.startswith('#')]
    
    def check_package_installed(self, package_name):
        """Check if a package is installed"""
        try:
            # Handle package names with version specifiers
            pkg_name = package_name.split('>=')[0].split('==')[0].split('<')[0]
            importlib.import_module(pkg_name.replace('-', '_'))
            return True
        except ImportError:
            return False
    
    def install_package(self, package):
        """Install a single package using pip"""
        try:
            cmd = [sys.executable, "-m", "pip", "install", package, "--quiet", "--no-warn-script-location"]
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=300)
            
            if result.returncode == 0:
                print(f"✓ Installed {package}")
                return True
            else:
                print(f"✗ Failed to install {package}: {result.stderr}")
                return False
        except subprocess.TimeoutExpired:
            print(f"✗ Timeout installing {package}")
            return False
        except Exception as e:
            print(f"✗ Error installing {package}: {str(e)}")
            return False
    
    def setup_dependencies(self):
        """Set up all required dependencies"""
        if self.setup_complete_file.exists():
            return True  # Setup already completed
        
        print("Setting up Python analytics dependencies...")
        
        try:
            self.check_python_version()
            print(f"✓ Python {sys.version.split()[0]} detected")
        except RuntimeError as e:
            print(f"✗ {str(e)}")
            return False
        
        required_packages = self.get_required_packages()
        missing_packages = []
        
        # Check which packages are missing
        for package in required_packages:
            pkg_name = package.split('>=')[0].split('==')[0].split('<')[0]
            if not self.check_package_installed(pkg_name):
                missing_packages.append(package)
        
        if not missing_packages:
            print("✓ All dependencies already installed")
            self.setup_complete_file.touch()
            return True
        
        print(f"Installing {len(missing_packages)} missing packages...")
        
        # Try to upgrade pip first
        try:
            subprocess.run([sys.executable, "-m", "pip", "install", "--upgrade", "pip", "--quiet"], 
                         timeout=60, capture_output=True)
        except:
            pass  # Continue even if pip upgrade fails
        
        # Install missing packages
        failed_packages = []
        for package in missing_packages:
            if not self.install_package(package):
                failed_packages.append(package)
        
        if failed_packages:
            print(f"✗ Failed to install: {', '.join(failed_packages)}")
            return False
        
        print("✓ All dependencies installed successfully")
        self.setup_complete_file.touch()
        return True
    
    def verify_database_connection(self, host='localhost', user='root', password='', database='air_quality_monitoring'):
        """Verify database connection"""
        try:
            import pymysql
            connection = pymysql.connect(
                host=host,
                user=user,
                password=password,
                database=database,
                charset='utf8mb4'
            )
            connection.close()
            print("✓ Database connection verified")
            return True
        except Exception as e:
            print(f"✗ Database connection failed: {str(e)}")
            return False
    
    def run_analytics_service(self, args):
        """Run the analytics service with proper error handling"""
        try:
            # Ensure dependencies are set up
            if not self.setup_dependencies():
                return False, "Failed to set up dependencies"
            
            # Import and run the analytics service
            from database_analytics import main as analytics_main
            
            # Temporarily replace sys.argv
            old_argv = sys.argv
            sys.argv = ['database_analytics.py'] + args
            
            try:
                analytics_main()
                return True, "Analytics completed successfully"
            finally:
                sys.argv = old_argv
                
        except ImportError as e:
            return False, f"Failed to import analytics module: {str(e)}"
        except Exception as e:
            return False, f"Analytics execution failed: {str(e)}"
    
    def health_check(self):
        """Perform a health check of the analytics system"""
        status = {
            "python_version": f"{sys.version.split()[0]}",
            "dependencies": {},
            "database": False,
            "overall": False
        }
        
        try:
            self.check_python_version()
            status["python_ok"] = True
        except:
            status["python_ok"] = False
            return status
        
        # Check dependencies
        required_packages = self.get_required_packages()
        for package in required_packages:
            pkg_name = package.split('>=')[0].split('==')[0].split('<')[0]
            status["dependencies"][pkg_name] = self.check_package_installed(pkg_name)
        
        # Check if all dependencies are available
        all_deps_ok = all(status["dependencies"].values())
        
        # Check database connection (with default settings)
        db_ok = self.verify_database_connection()
        status["database"] = db_ok
        
        status["overall"] = status["python_ok"] and all_deps_ok and db_ok
        
        return status

def main():
    """Main entry point for service manager"""
    manager = PythonServiceManager()
    
    if len(sys.argv) < 2:
        print("Usage: python service_manager.py <command> [args...]")
        print("Commands:")
        print("  setup          - Set up dependencies")
        print("  health         - Perform health check")
        print("  run [args...]  - Run analytics service")
        sys.exit(1)
    
    command = sys.argv[1]
    
    if command == "setup":
        success = manager.setup_dependencies()
        sys.exit(0 if success else 1)
    
    elif command == "health":
        status = manager.health_check()
        print(json.dumps(status, indent=2))
        sys.exit(0 if status["overall"] else 1)
    
    elif command == "run":
        args = sys.argv[2:]  # Pass remaining arguments to analytics service
        success, message = manager.run_analytics_service(args)
        if not success:
            print(f"Error: {message}", file=sys.stderr)
            sys.exit(1)
        sys.exit(0)
    
    else:
        print(f"Unknown command: {command}")
        sys.exit(1)

if __name__ == "__main__":
    main()
