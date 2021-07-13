package com.softspace.payment.hsm.controller;

import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping(value = { "/" })
public class HomeController {

	@RequestMapping(value = { "" })
	public void defResp(final HttpServletResponse response) {

		// always mark it as ok and never show error page
		response.setStatus(HttpServletResponse.SC_OK);
	}

}
