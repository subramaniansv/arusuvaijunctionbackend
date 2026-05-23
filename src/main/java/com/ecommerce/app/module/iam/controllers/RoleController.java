package com.ecommerce.app.module.iam.controllers;
import jakarta.servlet.http.*;

import com.ecommerce.app.module.iam.mapper.RoleConverterUtil;
import com.ecommerce.app.module.iam.models.ApiResponse;
import com.ecommerce.app.module.iam.models.Role;
import com.ecommerce.app.module.iam.security.RequiresRole;
import com.ecommerce.app.module.iam.services.RoleService;
import com.ecommerce.app.module.iam.util.SendResponseUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import java.io.IOException;
@WebServlet("/api/role")
@RequiresRole("Admin")
public class RoleController extends HttpServlet{
    RoleService service = new RoleService();
    public void doPost(HttpServletRequest request ,HttpServletResponse response) throws IOException,ServletException {
        Role role = RoleConverterUtil.requestToDto(request);
       role =  service.create(role);
       if(role.getId() !=null){
        SendResponseUtil.sendResponse(new ApiResponse(true, "role created", role, 200), response);
       }else{
        SendResponseUtil.sendResponse(new ApiResponse(false, "role not created", role, 400), response);
       }
    }

    public void doGet(HttpServletRequest request ,HttpServletResponse response) throws IOException,ServletException{
        String raw = request.getParameter("roleId");
        if(raw != null){
            Long roleID;
            try {
                roleID = Long.parseLong(raw);
            } catch (NumberFormatException nfe) {
                SendResponseUtil.sendResponse(new ApiResponse(false, "invalid roleId", null, 400), response);
                return;
            }
            Role role = service.getRoleById(roleID);
            SendResponseUtil.sendResponse(new ApiResponse(true, "role fetched", role, 200), response);
        }else{
            SendResponseUtil.sendResponse(new ApiResponse(true, "all roles fetched", service.getAllRoles(), 200), response);
        }
    }

 public void doDelete(HttpServletRequest request ,HttpServletResponse response) throws IOException,ServletException{
        String raw = request.getParameter("roleId");
        if(raw == null){
             SendResponseUtil.sendResponse(new ApiResponse(false, "id is required", null, 400), response);
             return;
        }
        Long roleID;
        try {
            roleID = Long.parseLong(raw);
        } catch (NumberFormatException nfe) {
            SendResponseUtil.sendResponse(new ApiResponse(false, "invalid roleId", null, 400), response);
            return;
        }
        service.deleteRoleById(roleID);
        SendResponseUtil.sendResponse(new ApiResponse(true, "role deleted", null, 200), response);
    }

}
