package com.softspace.payment.hsm.controller;

import java.io.IOException;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.softspace.payment.hsm.Utils;
import com.softspace.payment.hsm.bean.JSONServiceDTO;
import com.softspace.payment.hsm.common.Constants;
import com.softspace.payment.hsm.service.HSMService;

@Controller
@RequestMapping(value = { "/hsm", "/hsm/**" })
public class HSMServiceController {

	private static final Logger logger = LoggerFactory.getLogger(HSMServiceController.class);

	@Autowired
	@Qualifier(value = "hsmService")
	HSMService hsmService;

	@RequestMapping(value = { "" }, method = RequestMethod.GET)
	@ResponseBody
	public String defResp(final HttpServletResponse response) throws IOException {
		logger.debug("Acknowledged...");

		return "";
	}

	@RequestMapping(value = { "/getipek" }, method = RequestMethod.POST)
	@ResponseBody
	public String getIPEK(@RequestBody JSONServiceDTO dto) {
		try {
			logger.debug("Getting ipek...");

			String ipek = null;
			Map<String, Object> map = hsmService.getIPEK(dto.getKsn());
			if (map.get(Constants.PARAM_IPEK) != null) {
				ipek = (String) map.get(Constants.PARAM_IPEK);
			}
			return ipek;
		} catch (Exception e) {
			logger.error("Exception in getIPEK ===> ", e);
			return null;
		}
	}

	@RequestMapping(value = { "/encrypt" }, method = RequestMethod.POST)
	@ResponseBody
	public String encrypt(@RequestBody JSONServiceDTO dto) {
		try {
			return hsmService.encrypt(dto.getKsn(), Utils.hexStringToByteArray(dto.getPlainText()));
		} catch (Exception e) {
			logger.error("Exception in encrypt ===> ", e);
			return null;
		}
	}

	@RequestMapping(value = { "/decrypt" }, method = RequestMethod.POST)
	@ResponseBody
	public String decrypt(@RequestBody JSONServiceDTO dto) {
		try {
			return hsmService.decrypt(dto.getKsn(), dto.getCipherText());
		} catch (Exception e) {
			logger.error("Exception in encrypt ===> ", e);
			return null;
		}
	}

	@RequestMapping(value = { "/encryptAES" }, method = RequestMethod.POST)
	@ResponseBody
	public String encryptAES(@RequestBody JSONServiceDTO dto) {
		try {
			return hsmService.encryptAES(Utils.hexStringToByteArray(dto.getPlainText()),
					Utils.hexStringToByteArray(dto.getEncryptedWorkingKey()), Utils.hexStringToByteArray(dto.getInitialVector()));
		} catch (Exception e) {
			logger.error("Exception in encrypt ===> ", e);
			return null;
		}
	}

	@RequestMapping(value = { "/decryptAES" }, method = RequestMethod.POST)
	@ResponseBody
	public String decryptAES(@RequestBody JSONServiceDTO dto) {
		try {
			return hsmService.decryptAES(Utils.hexStringToByteArray(dto.getCipherText()),
					Utils.hexStringToByteArray(dto.getEncryptedWorkingKey()), Utils.hexStringToByteArray(dto.getInitialVector()));
		} catch (Exception e) {
			logger.error("Exception in encrypt ===> ", e);
			return null;
		}
	}
}
