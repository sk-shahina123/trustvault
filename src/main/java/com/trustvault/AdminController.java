package com.trustvault;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminController {

    @GetMapping("/api/admin/check")
    public String checkAdminAccess() {
        return "Admin access confirmed";
    }
}
