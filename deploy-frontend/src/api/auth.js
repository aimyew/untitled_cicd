import request from './request'

export const authApi = {
  login: (data) => request.post('/auth/login', data),
  me: () => request.get('/auth/me'),
  logout: () => request.post('/auth/logout'),
  changePassword: (data) => request.post('/auth/change-password', data)
}

export const userApi = {
  list: (params = {}) => request.get('/users', { params }),
  funcPermDefs: () => request.get('/users/func-perm-defs'),
  updateStatus: (id, status) => request.post(`/users/${id}/status`, { status }),
  updateNickname: (id, nickname) => request.post(`/users/${id}/nickname`, { nickname }),
  resetPassword: (id, newPassword) => request.post(`/users/${id}/reset-password`, { newPassword }),
  updatePermissions: (id, codes) => request.post(`/users/${id}/permissions`, { codes }),
  updateDeployPermissions: (id, projectIds) =>
    request.post(`/users/${id}/deploy-permissions`, { projectIds })
}

export const menuApi = {
  list: () => request.get('/menus'),
  currentVisible: () => request.get('/menus/current-visible'),
  create: (data) => request.post('/menus', data),
  update: (id, data) => request.put(`/menus/${id}`, data),
  remove: (id) => request.delete(`/menus/${id}`)
}

export const auditApi = {
  page: (params) => request.get('/audit-logs', { params })
}
