import subprocess
import logging

def run_ssh_command(remote_host: str, command: str, verbose: bool = False) -> tuple[int, str, str]:
    """
    Run a shell command on a remote host via SSH.
    """
    result = subprocess.run(
        ["ssh", remote_host, command],
        capture_output=True,
        text=True
    )
    if verbose:
        logging.info(f"Executed {command}. Exit code {result.returncode} STDOUT: {result.stdout}")
    if result.returncode != 0:
        logging.error(f"Failed with STDERR: {result.stderr}")
    return result.returncode, result.stdout, result.stderr