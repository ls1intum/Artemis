import java.nio.file.Path;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

class Examples {

    // artemis-request-value-in-file-path
    @GetMapping("a")
    String readFile(@RequestParam String name) {
        // ruleid: artemis-request-value-in-file-path
        return Path.of("/data", name).toString();
    }

    @GetMapping("b")
    String readFileWithFixedName(@RequestParam String name) {
        // ok: artemis-request-value-in-file-path
        return Path.of("/data", "fixed.txt").toString();
    }

    // artemis-request-value-in-process
    @PostMapping("c")
    void run(@PathVariable String command) throws Exception {
        // ruleid: artemis-request-value-in-process
        new ProcessBuilder(command).start();
    }

    @PostMapping("d")
    void runFixed(@PathVariable String command) throws Exception {
        // ok: artemis-request-value-in-process
        new ProcessBuilder("ls").start();
    }

    // artemis-forwarded-header-read-directly
    String address(HttpServletRequest request) {
        // ruleid: artemis-forwarded-header-read-directly
        return request.getHeader("X-Forwarded-For");
    }

    String agent(HttpServletRequest request) {
        // ok: artemis-forwarded-header-read-directly
        return request.getHeader("User-Agent");
    }

}
